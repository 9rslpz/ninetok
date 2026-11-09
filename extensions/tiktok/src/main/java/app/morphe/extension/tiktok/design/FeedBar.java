/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.design;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * TikTok's tab bar (Home, Friends, +, Inbox, Profile) stays black over the video feed, as in
 * TikTok's own themes, whichever of Light, Grey or Black is on. Grey moves TikTok's dark black
 * onto graphite, the bar's fill included; on Home the bar gets black back, and on the other tabs
 * it has its fill from the theme again.
 */
final class FeedBar {
    /** TikTok 47.4.3's ids: the tab bar and the Home tab's page. */
    private static final String BAR = "onp";
    private static final String HOME_PAGE = "ir7";
    /** Holds the bar's own fill while it is painted black over Home. */
    private static final int SAVED = 0x7f7f4051;
    private static final int BLACK = 0xFF000000;

    private FeedBar() {
    }

    static void apply(View root) {
        View bar = byName(root, BAR);
        if (bar == null) return;
        View home = byName(root, HOME_PAGE);
        boolean onHome = home != null && home.isShown();
        Object saved = bar.getTag(SAVED);
        if (onHome) {
            Drawable fill = bar.getBackground();
            if (fill instanceof ColorDrawable && ((ColorDrawable) fill).getColor() == BLACK) return;
            if (saved == null) bar.setTag(SAVED, new Holder(fill));
            bar.setBackgroundColor(BLACK);
        } else if (saved instanceof Holder) {
            bar.setTag(SAVED, null);
            bar.setBackground(((Holder) saved).fill);
        }
    }

    /** Whether TikTok's tab bar is on screen over the Home feed. */
    static boolean isBar(View view) {
        int id = view.getId();
        return id != View.NO_ID && id == idOf(view, BAR);
    }

    private static int idOf(View view, String name) {
        return view.getResources().getIdentifier(name, "id", view.getContext().getPackageName());
    }

    private static View byName(View root, String name) {
        int id = idOf(root, name);
        return id == 0 ? null : root.findViewById(id);
    }

    /** A view tag holding a drawable, or null for a bar that had none. */
    private static final class Holder {
        final Drawable fill;

        Holder(Drawable fill) {
            this.fill = fill;
        }
    }
}
