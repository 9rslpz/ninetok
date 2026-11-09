/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.design;

import android.app.Activity;
import android.app.Application;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.ColorStateListDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.shared.GlobalLayoutHook;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * NINE’tok's design on every TikTok screen, in Light, Grey and Black alike: the surfaces carry
 * the fine dot grid of NINE’tok's own settings, and in Light the white pages turn its cream.
 *
 * <p>On the layout passes of each window it looks at every view filled with a plain colour a
 * screen is built on: in dark mode a dark one, in light mode a near-white one. Pages, headers,
 * cards and sheets (at least 60% of the window wide and 40 dp tall) take that fill back with the
 * grid over it, keeping their rounded corners; the dots sit on the window's grid, so neighbouring
 * surfaces line up. A row of a list filled like the surface under it is made see-through, so the
 * grid shows under it too. TikTok's tab bar takes the fill without dots. The video feed, the
 * camera, LIVE, text, buttons and fields are left as they are.
 */
public final class PageDots {
    private static final int SPACING_DP = 14;
    private static final float RADIUS_DP = 0.8f;
    private static final int LIGHT_DOT = 0x17141413;
    private static final int DARK_DOT = 0x1CFAF9F5;
    private static final long MIN_GAP_MS = 250;
    /** NINE’tok's light page colour, the cream of its own settings. */
    private static final int CREAM = 0xFFFAF9F5;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Activity, GlobalLayoutHook> HOOKS = new WeakHashMap<>();
    private static final Map<View, Long> LAST_RUN = new WeakHashMap<>();
    private static Application watching;

    private static final Application.ActivityLifecycleCallbacks WINDOWS = new Application.ActivityLifecycleCallbacks() {
        @Override public void onActivityResumed(Activity activity) {
            TikTokTheme.remember(activity);
            watch(activity);
        }

        @Override public void onActivityDestroyed(Activity activity) {
            GlobalLayoutHook hook = HOOKS.remove(activity);
            if (hook != null) hook.detach();
        }

        @Override public void onActivityCreated(Activity activity, Bundle state) { }
        @Override public void onActivityStarted(Activity activity) { }
        @Override public void onActivityPaused(Activity activity) { }
        @Override public void onActivityStopped(Activity activity) { }
        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
    };

    private PageDots() {
    }

    /** From the patched main activity's onCreate; touches no view, waits for each resume. */
    public static void install(Activity activity) {
        try {
            Application application = activity.getApplication();
            if (application == null || application == watching) return;
            if (watching != null) watching.unregisterActivityLifecycleCallbacks(WINDOWS);
            watching = application;
            application.registerActivityLifecycleCallbacks(WINDOWS);
        } catch (Throwable error) {
            Logger.printException(() -> "Could not watch TikTok's pages for the dot grid", error);
        }
    }

    private static void watch(Activity activity) {
        try {
            if (skipped(activity)) return;
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            GlobalLayoutHook hook = HOOKS.get(activity);
            if (hook == null) {
                hook = new GlobalLayoutHook();
                HOOKS.put(activity, hook);
            }
            hook.install(root, () -> schedule(root));
            schedule(root);
        } catch (Throwable error) {
            Logger.printException(() -> "Could not watch a window for the dot grid", error);
        }
    }

    /** The camera, the editors and LIVE draw over video of their own: no grid there. */
    private static boolean skipped(Activity activity) {
        String name = activity.getClass().getName().toLowerCase();
        return name.contains("shortvideo") || name.contains("camera") || name.contains("record")
                || name.contains("live") || name.contains("creative") || name.contains("player");
    }

    /** At most one pass every quarter second per window, and one more after the last change. */
    private static void schedule(ViewGroup root) {
        long now = SystemClock.uptimeMillis();
        Long last = LAST_RUN.get(root);
        if (last != null && now - last < MIN_GAP_MS) {
            MAIN.removeCallbacksAndMessages(root);
            MAIN.postAtTime(() -> run(root), root, last + MIN_GAP_MS);
            return;
        }
        run(root);
    }

    private static void run(ViewGroup root) {
        try {
            LAST_RUN.put(root, SystemClock.uptimeMillis());
            if (!Settings.NINETOK_PAGE_DOTS.get() || !root.isAttachedToWindow()) return;
            // Black over the feed in every theme, as in TikTok's own: before the grid looks at it.
            FeedBar.apply(root);
            boolean dark = TikTokTheme.isDark(root.getContext());
            float density = root.getResources().getDisplayMetrics().density;
            int minWidth = root.getWidth() * 6 / 10;
            int minHeight = Math.round(40 * density);
            // The Home tab's feed and the views holding it get no grid: the black around the
            // videos stays black.
            View home = homePage(root);
            java.util.Set<View> aroundHome = new java.util.HashSet<>();
            for (Object parent = home == null ? null : home.getParent(); parent instanceof View;
                 parent = ((View) parent).getParent()) {
                aroundHome.add((View) parent);
            }
            ArrayDeque<View> queue = new ArrayDeque<>();
            queue.add(root);
            while (!queue.isEmpty()) {
                View view = queue.poll();
                if (view.getVisibility() != View.VISIBLE || isVideo(view) || view == home) continue;
                if (aroundHome.contains(view)) {
                    if (view instanceof ViewGroup) {
                        ViewGroup group = (ViewGroup) view;
                        for (int i = 0; i < group.getChildCount(); i++) queue.add(group.getChildAt(i));
                    }
                    continue;
                }
                if (ComposeDots.isCompose(view)) {
                    // A Compose screen: no views inside to dress, the grid goes on as a shader.
                    ComposeDots.apply(view, view.getWidth() >= minWidth && view.getHeight() >= minHeight);
                    continue;
                }
                dress(view, root, dark, minWidth, minHeight);
                if (view instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) view;
                    for (int i = 0; i < group.getChildCount(); i++) queue.add(group.getChildAt(i));
                }
            }
        } catch (Throwable error) {
            Logger.printException(() -> "Could not lay the dot grid on a screen", error);
        }
    }

    /** Checked on every pass: a recycled row gets TikTok's fill back when it is bound. */
    private static void dress(View view, View root, boolean dark, int minWidth, int minHeight) {
        Drawable background = view.getBackground();
        if (background == null || background instanceof Dots) return;
        if (view instanceof TextView || view instanceof EditText || holdsField(view)) return;
        // TikTok's tab bar keeps its own fill (black over the feed, see FeedBar).
        if (FeedBar.isBar(view)) return;
        Fill fill = fill(background);
        if (fill == null || !surface(fill.color, dark)) return;
        int color = !dark && fill.color == 0xFFFFFFFF ? CREAM : fill.color;

        if (inList(view) && fill.radii == null) {
            Integer behind = dottedBehind(view);
            if (behind != null && near(behind, color)) {
                view.setBackground(null);
                return;
            }
        }
        boolean bottomBar = fill.radii == null && isBottomBar(view, root);
        if (bottomBar) {
            view.setBackground(new Dots(color, false, null));
            return;
        }
        if (view.getWidth() >= minWidth && view.getHeight() >= minHeight) {
            view.setBackground(new Dots(color, true, fill.radii));
        }
    }

    /** TikTok 47.4.3's Home tab page ("ir7"), which holds the video feed, or null. */
    private static View homePage(View root) {
        int id = root.getResources().getIdentifier("ir7", "id", root.getContext().getPackageName());
        return id == 0 ? null : root.findViewById(id);
    }

    /** A dark surface in dark mode, a near-white one in light mode: not an accent or a button. */
    private static boolean surface(int color, boolean dark) {
        float luminance = luminance(color);
        return dark ? luminance < 0.32f : luminance > 0.86f;
    }

    /** A search box or other field's frame: its fill stays plain. */
    private static boolean holdsField(View view) {
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i) instanceof EditText) return true;
        }
        return false;
    }

    /** The feed's video pages and anything playing video stay out of it, with all they hold. */
    private static boolean isVideo(View view) {
        if (view instanceof SurfaceView || view instanceof TextureView) return true;
        String name = view.getClass().getName();
        return name.endsWith("VideoViewCellRootView") || name.endsWith("VerticalViewPager");
    }

    /** A row of a scrolling list, which never stays put over other rows the way a header does. */
    private static boolean inList(View view) {
        Object parent = view.getParent();
        for (int depth = 0; parent instanceof View && depth < 4; depth++, parent = ((View) parent).getParent()) {
            String name = parent.getClass().getName();
            if (name.endsWith("RecyclerView") || parent instanceof android.widget.AbsListView) return true;
        }
        return false;
    }

    /** The fill of the first filled view behind this one when it is dotted, or null. */
    private static Integer dottedBehind(View view) {
        for (Object parent = view.getParent(); parent instanceof View; parent = ((View) parent).getParent()) {
            Drawable background = ((View) parent).getBackground();
            if (background == null) continue;
            return background instanceof Dots ? ((Dots) background).color : null;
        }
        return null;
    }

    private static boolean near(int a, int b) {
        return Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)) <= 6
                && Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)) <= 6
                && Math.abs((a & 0xFF) - (b & 0xFF)) <= 6;
    }

    /** TikTok's tab bar: a band across the window, under 100 dp tall, that ends at its bottom. */
    private static boolean isBottomBar(View view, View root) {
        float density = root.getResources().getDisplayMetrics().density;
        if (view.getWidth() < root.getWidth() * 9 / 10 || view.getHeight() > 100 * density) return false;
        int[] at = new int[2];
        int[] rootAt = new int[2];
        view.getLocationInWindow(at);
        root.getLocationInWindow(rootAt);
        return (rootAt[1] + root.getHeight()) - (at[1] + view.getHeight()) <= 8 * density;
    }

    private static float luminance(int color) {
        return (0.299f * ((color >> 16) & 0xFF) + 0.587f * ((color >> 8) & 0xFF)
                + 0.114f * (color & 0xFF)) / 255f;
    }

    /** A plain opaque fill and its corners (null when square). */
    private static final class Fill {
        final int color;
        final float[] radii;

        Fill(int color, float[] radii) {
            this.color = color;
            this.radii = radii;
        }
    }

    private static Fill fill(Drawable background) {
        if (background instanceof ColorDrawable) {
            int color = ((ColorDrawable) background).getColor();
            return (color >>> 24) == 0xFF ? new Fill(color, null) : null;
        }
        if (Build.VERSION.SDK_INT >= 29 && background instanceof ColorStateListDrawable) {
            ColorStateList list = ((ColorStateListDrawable) background).getColorStateList();
            if (list.isStateful()) return null;
            int color = list.getDefaultColor();
            return (color >>> 24) == 0xFF ? new Fill(color, null) : null;
        }
        if (background instanceof GradientDrawable) {
            GradientDrawable shape = (GradientDrawable) background;
            if (shape.getShape() != GradientDrawable.RECTANGLE || shape.getColors() != null) return null;
            ColorStateList list = shape.getColor();
            if (list == null || list.isStateful()) return null;
            int color = list.getDefaultColor();
            if ((color >>> 24) != 0xFF) return null;
            float[] radii = shape.getCornerRadii();
            float radius = shape.getCornerRadius();
            if (radii == null && radius > 0f) radii = new float[]{radius, radius, radius, radius, radius, radius, radius, radius};
            return new Fill(color, radii);
        }
        return null;
    }

    /** A surface's colour with the grid over it, the dots set by the window, not by the view. */
    static final class Dots extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int color;
        private final boolean dotted;
        private final float[] radii;
        private final int[] at = new int[2];
        private final Path clip = new Path();
        private final RectF box = new RectF();

        Dots(int color, boolean dotted, float[] radii) {
            this.color = color;
            this.dotted = dotted;
            this.radii = radii;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            int save = canvas.save();
            if (radii != null) {
                box.set(bounds);
                clip.reset();
                clip.addRoundRect(box, radii, Path.Direction.CW);
                canvas.clipPath(clip);
            }
            paint.setColor(color);
            canvas.drawRect(bounds, paint);
            if (dotted && Settings.NINETOK_PAGE_DOTS.get()) drawDots(canvas, bounds);
            canvas.restoreToCount(save);
        }

        private void drawDots(Canvas canvas, Rect bounds) {
            float density = android.content.res.Resources.getSystem().getDisplayMetrics().density;
            float spacing = SPACING_DP * density;
            float radius = Math.max(0.6f, RADIUS_DP * density);
            paint.setColor(luminance(color) < 0.5f ? DARK_DOT : LIGHT_DOT);
            Object callback = getCallback();
            int originX = 0;
            int originY = 0;
            if (callback instanceof View) {
                ((View) callback).getLocationInWindow(at);
                originX = at[0];
                originY = at[1];
            }
            // The first dot at or after the bounds' edge, on the window's grid.
            float startX = bounds.left + mod(spacing / 2f - originX - bounds.left, spacing);
            float startY = bounds.top + mod(spacing / 2f - originY - bounds.top, spacing);
            for (float y = startY; y < bounds.bottom; y += spacing) {
                for (float x = startX; x < bounds.right; x += spacing) {
                    canvas.drawCircle(x, y, radius, paint);
                }
            }
        }

        private static float mod(float value, float by) {
            float result = value % by;
            return result < 0 ? result + by : result;
        }

        /** Keeps the shadow a card or sheet had, rounded as it was. */
        @Override
        public void getOutline(Outline outline) {
            Rect bounds = getBounds();
            if (radii == null) {
                outline.setRect(bounds);
            } else {
                outline.setRoundRect(bounds, radii[0]);
            }
        }

        @Override public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override public void setColorFilter(ColorFilter filter) {
            paint.setColorFilter(filter);
        }

        @Override public int getOpacity() {
            return radii == null ? PixelFormat.OPAQUE : PixelFormat.TRANSLUCENT;
        }
    }
}
