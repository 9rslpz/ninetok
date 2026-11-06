/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.inbox;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.motion.Motion;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * The streak flame by a chat's name. When the streak goes up TikTok rolls the number and plays a
 * Lottie flame burst in place of the icon. With "Calm streak flame" on, TikTok puts the new icon
 * and number straight in and the badge fades in from a little smaller, in one smooth move.
 */
public final class StreakFlame {
    private StreakFlame() {
    }

    /**
     * Called by the badge's update in place of TikTok's "animate the update" flag.
     *
     * @param badge          the streak badge
     * @param tiktokAnimates the flag TikTok read
     * @param lit            the streak went from grey to orange
     * @param appeared       the badge came in already lit
     * @return whether TikTok plays its own animation
     */
    public static boolean updateAnim(View badge, boolean tiktokAnimates, boolean lit, boolean appeared) {
        try {
            if (!Settings.CALM_STREAK_FLAME.get()) return tiktokAnimates;
            if (tiktokAnimates && (lit || appeared) && badge != null) glow(badge);
            return false;
        } catch (Throwable error) {
            Logger.printException(() -> "Could not calm the streak flame", error);
            return tiktokAnimates;
        }
    }

    private static void glow(View badge) {
        badge.animate().cancel();
        badge.setAlpha(Motion.FLAME_FROM_ALPHA);
        badge.setScaleX(Motion.FLAME_FROM_SCALE);
        badge.setScaleY(Motion.FLAME_FROM_SCALE);
        badge.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(Motion.FLAME_MS)
                .setInterpolator(Motion.ARRIVE)
                .withLayer()
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationCancel(Animator animation) {
                        // A recycled row or a newer update: never leave the badge half faded.
                        badge.setAlpha(1f);
                        badge.setScaleX(1f);
                        badge.setScaleY(1f);
                    }
                })
                .start();
    }
}
