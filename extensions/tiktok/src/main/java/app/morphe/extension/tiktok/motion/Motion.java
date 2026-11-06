/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.motion;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.view.animation.PathInterpolator;

import app.morphe.extension.tiktok.settings.Settings;

/**
 * NINE’tok's motion: the timings and curves every animation it adds shares, so they move alike.
 *
 * <p>Medium weight on purpose: one {@link ValueAnimator} per transition, driving view properties
 * the render thread composes (translation, alpha, scale), no bitmaps and no layout during the
 * motion. Android's own animator scale applies, so "Remove animations" in the phone's settings
 * stops these too.
 */
public final class Motion {
    /** Starts fast and settles slowly: things arriving. Material's emphasized decelerate. */
    public static final TimeInterpolator ARRIVE = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    /** Leaves gently and speeds away: things going. Material's emphasized accelerate. */
    public static final TimeInterpolator LEAVE = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);
    /** Even in and out, for moves that start and end on screen. */
    public static final TimeInterpolator STANDARD = new PathInterpolator(0.2f, 0f, 0f, 1f);

    /** How long a switch between the main tabs takes, in milliseconds. */
    public static final long TAB_SWITCH_MS = 300;
    /** How far the tab page going slides as it fades, as a share of the page's width. */
    public static final float TAB_TRAVEL = 0.10f;
    /**
     * How much the tab page coming overflows each side as the swap starts, as a share of its
     * width. It is scaled up by twice this and shifted by this toward the side it comes from.
     */
    public static final float TAB_SETTLE = 0.02f;

    /** How long the streak flame takes to fade in when a streak goes up, in milliseconds. */
    public static final long FLAME_MS = 420;
    /** Where the streak flame starts as it fades in. */
    public static final float FLAME_FROM_ALPHA = 0.35f;
    public static final float FLAME_FROM_SCALE = 0.86f;

    private Motion() {
    }

    /** Whether NINE’tok's tab animations are on and the phone animates at all. */
    public static boolean tabsOn() {
        return Settings.MAIN_TAB_ANIMATIONS.get() && ValueAnimator.areAnimatorsEnabled();
    }

    /**
     * Where {@code t} (0 to 1 over the whole animation) is inside the part from {@code from} to
     * {@code to}, as 0 to 1, run through {@code curve}.
     */
    public static float part(float t, float from, float to, TimeInterpolator curve) {
        if (t <= from) return 0f;
        if (t >= to) return 1f;
        return curve.getInterpolation((t - from) / (to - from));
    }
}
