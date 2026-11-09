/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.design;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.util.TypedValue;

/**
 * Whether TikTok shows its dark mode. TikTok switches light and dark by theme, not by Android's
 * night mode: its themes set the page colour attribute ("bx", BGPrimary on 47.4.3) to white or
 * to a dark gray, whatever the phone's own dark mode says. So this reads that attribute off the
 * screen's theme, and remembers the last answer a TikTok screen gave for screens whose theme
 * does not carry it.
 */
public final class TikTokTheme {
    private static final String PAGE_ATTR = "bx";
    private static int pageAttr = -1;
    private static Boolean last;

    private TikTokTheme() {
    }

    /** TikTok's dark mode, read from {@code context}'s theme. */
    public static boolean isDark(Context context) {
        Boolean answer = fromTheme(context);
        if (answer != null) {
            last = answer;
            return answer;
        }
        if (last != null) return last;
        int night = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Called as TikTok's screens come up, so a screen without the attribute still knows. */
    public static void remember(Activity activity) {
        Boolean answer = fromTheme(activity);
        if (answer != null) last = answer;
    }

    private static Boolean fromTheme(Context context) {
        try {
            if (context == null) return null;
            if (pageAttr == -1) {
                pageAttr = context.getResources().getIdentifier(PAGE_ATTR, "attr", context.getPackageName());
            }
            if (pageAttr == 0) return null;
            TypedValue value = new TypedValue();
            if (!context.getTheme().resolveAttribute(pageAttr, value, true)) return null;
            if (value.type < TypedValue.TYPE_FIRST_COLOR_INT || value.type > TypedValue.TYPE_LAST_COLOR_INT) return null;
            int color = value.data;
            float luminance = (0.299f * ((color >> 16) & 0xFF) + 0.587f * ((color >> 8) & 0xFF)
                    + 0.114f * (color & 0xFF)) / 255f;
            return luminance < 0.5f;
        } catch (Throwable error) {
            return null;
        }
    }
}
