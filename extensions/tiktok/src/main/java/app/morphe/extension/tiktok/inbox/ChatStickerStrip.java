/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.inbox;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.shared.GlobalLayoutHook;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * The sticker suggestions TikTok slides in above the chat box while you type. They come up for
 * almost every word and take a band about a fifth of the screen tall, so they are drawn at 30% of
 * their size unless "Full-size sticker suggestions" is on.
 *
 * <p>The strip is a horizontal list (resource id "xhe" in TikTok 47.4.3) whose items TikTok sizes
 * as it binds them. Each layout pass sizes the strip, its holder and every item from the size
 * TikTok first gave them, so a rebound item is put back at the small size and a pass that finds
 * them already sized changes nothing.
 */
public final class ChatStickerStrip {
    private static final float SMALL = 0.3f;
    /** View tag holding a view's own layout size, before this class scaled it. */
    private static final int ORIGINAL_SIZE = 0x7f7f4011;
    private static final String STRIP_ID = "xhe";

    private static final Map<Activity, GlobalLayoutHook> HOOKS = new WeakHashMap<>();
    private static int stripId = -1;

    private ChatStickerStrip() {
    }

    /** Watches an activity's window for the strip. Called for every activity TikTok opens. */
    public static void install(Activity activity) {
        Window window = activity.getWindow();
        if (window == null || !(window.getDecorView() instanceof ViewGroup)) return;
        ViewGroup root = (ViewGroup) window.getDecorView();
        GlobalLayoutHook hook;
        synchronized (HOOKS) {
            hook = HOOKS.get(activity);
            if (hook == null) {
                hook = new GlobalLayoutHook();
                HOOKS.put(activity, hook);
            }
        }
        hook.install(root, () -> apply(root));
    }

    private static void apply(ViewGroup root) {
        if (stripId == -1) {
            stripId = root.getResources().getIdentifier(STRIP_ID, "id", root.getContext().getPackageName());
        }
        if (stripId == 0) return;
        View strip = root.findViewById(stripId);
        if (!(strip instanceof ViewGroup) || !strip.isShown()) return;
        float scale = Settings.FULL_SIZE_CHAT_STICKERS.get() ? 1f : SMALL;
        // The strip and its holder keep their full width: only how tall they are follows the items.
        resize(strip, scale, false);
        if (strip.getParent() instanceof View) resize((View) strip.getParent(), scale, false);
        ViewGroup list = (ViewGroup) strip;
        for (int i = 0; i < list.getChildCount(); i++) resize(list.getChildAt(i), scale, true);
    }

    /**
     * Sets a view to {@code scale} of the size it had before. An item TikTok sizes to its content
     * takes the size it was first laid out at as its own; the strip and holder keep wrap and match.
     */
    private static void resize(View view, float scale, boolean item) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null) return;
        Object tag = view.getTag(ORIGINAL_SIZE);
        int[] original;
        if (tag instanceof int[]) {
            original = (int[]) tag;
        } else {
            int w = params.width > 0 || !item ? params.width : view.getWidth();
            int h = params.height > 0 || !item ? params.height : view.getHeight();
            if (item && (w <= 0 || h <= 0)) return; // not laid out yet: the next pass sizes it
            original = new int[]{w, h, view.getPaddingTop()};
            view.setTag(ORIGINAL_SIZE, original);
        }
        int width = original[0] > 0 && item ? Math.max(1, Math.round(original[0] * scale)) : params.width;
        int height = original[1] > 0 ? Math.max(1, Math.round(original[1] * scale)) : params.height;
        int top = Math.round(original[2] * scale);
        boolean changed = false;
        if (params.width != width || params.height != height) {
            params.width = width;
            params.height = height;
            changed = true;
        }
        if (view.getPaddingTop() != top) {
            view.setPadding(view.getPaddingLeft(), top, view.getPaddingRight(), view.getPaddingBottom());
        }
        if (changed) view.setLayoutParams(params);
    }
}
