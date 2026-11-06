/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.motion;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.TabHost;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.GlobalLayoutHook;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.navigation.FrontWindow;

/**
 * Home, Friends, Inbox and Profile swap with a slide and a fade instead of cutting over.
 *
 * <p>TikTok keeps each main tab's page as a child of one frame under its tab host and switches
 * by hiding one and showing the other. A {@link LayoutTransition} on that frame, with only its
 * "disappearing" kind on, keeps the page being hidden on screen while it animates. Its animator
 * drives both pages: the one going slides a little toward the side it came from and fades out
 * early, the one coming slides in from the other side and fades in. Which side is which comes
 * from the tab numbers, so moving right slides left and back again.
 *
 * <p>The frame is found on the layout passes of the main activity in front, and the transition
 * is taken off again when "Tab animations" is turned off.
 */
public final class MainTabMotion {
    /** The frame holding the tab pages, in TikTok 47.4.3. Found by shape when it isn't there. */
    private static final String PAGES_ID = "y9d";

    private static final GlobalLayoutHook LAYOUT = new GlobalLayoutHook();
    private static final FrontWindow FRONT = new FrontWindow(false, new FrontWindow.Listener() {
        @Override public void onFront(Activity activity) {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root != null) LAYOUT.install(root, MainTabMotion::apply);
            apply();
        }

        @Override public void onGone(Activity activity) {
            LAYOUT.detach();
        }
    });

    private static int pagesId = -1;
    private static WeakReference<ViewGroup> pages = new WeakReference<>(null);
    private static WeakReference<TabHost> host = new WeakReference<>(null);
    /** The tab shown before the switch now running, to tell which way it goes. */
    private static int lastTab = -1;
    private static Swap running;

    private MainTabMotion() {
    }

    /** From the patched main activity's onCreate. Touches no view: views wait for the resume. */
    public static void install(Context context) {
        try {
            if (context instanceof Activity) FRONT.add((Activity) context);
        } catch (Throwable error) {
            Logger.printException(() -> "Could not follow the main tabs", error);
        }
    }

    private static void apply() {
        try {
            Activity activity = FRONT.get();
            if (activity == null || activity.isFinishing()) return;
            ViewGroup frame = pages.get();
            if (frame == null || !frame.isAttachedToWindow()) {
                frame = findPages(activity);
                pages = new WeakReference<>(frame);
                host = new WeakReference<>(frame == null ? null : tabHostAbove(frame));
            }
            if (frame == null) return;
            LayoutTransition current = frame.getLayoutTransition();
            if (Motion.tabsOn()) {
                if (current == null) frame.setLayoutTransition(new TabTransition());
            } else if (current instanceof TabTransition) {
                frame.setLayoutTransition(null);
            }
            if (running == null) lastTab = currentTab();
        } catch (Throwable error) {
            Logger.printException(() -> "Could not set up the tab animations", error);
        }
    }

    private static int currentTab() {
        TabHost tabs = host.get();
        return tabs == null ? -1 : tabs.getCurrentTab();
    }

    /** The frame of tab pages: TikTok's id when it names a frame under the tab host, or by shape. */
    private static ViewGroup findPages(Activity activity) {
        if (pagesId == -1) {
            pagesId = activity.getResources().getIdentifier(PAGES_ID, "id", activity.getPackageName());
        }
        if (pagesId != 0) {
            View byId = activity.findViewById(pagesId);
            if (byId instanceof FrameLayout && tabHostAbove(byId) != null) return (ViewGroup) byId;
        }
        View content = activity.findViewById(android.R.id.content);
        TabHost tabs = first(content, TabHost.class);
        return tabs == null ? null : pagesUnder(tabs);
    }

    /** The first frame under the tab host holding two or more pages that fill it. */
    private static ViewGroup pagesUnder(TabHost tabs) {
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(tabs);
        while (!queue.isEmpty()) {
            View view = queue.poll();
            if (!(view instanceof ViewGroup)) continue;
            ViewGroup group = (ViewGroup) view;
            if (group instanceof FrameLayout && group != tabs) {
                int filling = 0;
                for (int i = 0; i < group.getChildCount(); i++) {
                    ViewGroup.LayoutParams params = group.getChildAt(i).getLayoutParams();
                    if (params != null && params.width == ViewGroup.LayoutParams.MATCH_PARENT
                            && params.height == ViewGroup.LayoutParams.MATCH_PARENT
                            && group.getChildAt(i) instanceof ViewGroup) filling++;
                }
                if (filling >= 2) return group;
            }
            for (int i = 0; i < group.getChildCount(); i++) queue.add(group.getChildAt(i));
        }
        return null;
    }

    private static TabHost tabHostAbove(View view) {
        for (Object parent = view.getParent(); parent instanceof View; parent = ((View) parent).getParent()) {
            if (parent instanceof TabHost) return (TabHost) parent;
        }
        return null;
    }

    private static <T> T first(View root, Class<T> type) {
        if (root == null) return null;
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            View view = queue.poll();
            if (type.isInstance(view)) return type.cast(view);
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) queue.add(group.getChildAt(i));
            }
        }
        return null;
    }

    /** Animates only pages being hidden or taken out; each one starts a {@link Swap}. */
    private static final class TabTransition extends LayoutTransition {
        TabTransition() {
            disableTransitionType(APPEARING);
            disableTransitionType(CHANGE_APPEARING);
            disableTransitionType(CHANGE_DISAPPEARING);
            disableTransitionType(CHANGING);
            enableTransitionType(DISAPPEARING);
            setStartDelay(DISAPPEARING, 0);
            setDuration(DISAPPEARING, Motion.TAB_SWITCH_MS);
            setInterpolator(DISAPPEARING, new LinearInterpolator());
            setAnimator(DISAPPEARING, ValueAnimator.ofFloat(0f, 1f));
            addTransitionListener(new TransitionListener() {
                @Override
                public void startTransition(LayoutTransition transition, ViewGroup parent, View view, int type) {
                    // Runs before the transition copies its animator for this page.
                    if (type == DISAPPEARING) setAnimator(DISAPPEARING, Swap.start(parent, view));
                }

                @Override
                public void endTransition(LayoutTransition transition, ViewGroup parent, View view, int type) {
                }
            });
        }
    }

    /** One switch: the page going and, found on the first frame, the page coming. */
    private static final class Swap extends AnimatorListenerAdapter implements ValueAnimator.AnimatorUpdateListener {
        private final WeakReference<ViewGroup> parent;
        private final View going;
        private View coming;
        /**
         * Video surfaces of the page going that this hid for the swap. A surface view clears a
         * hole through the window for its video, and once its page is hidden there is no video
         * behind the hole: drawn while the page fades, it shows as a black block.
         */
        private final List<View> hiddenSurfaces = new ArrayList<>();
        /** The copy of the animator the transition runs, so a newer switch can end it. */
        private ValueAnimator live;
        private int side; // +1: the new tab is to the right, -1: to the left
        private int frames;
        private boolean done;

        private Swap(ViewGroup parent, View going) {
            this.parent = new WeakReference<>(parent);
            this.going = going;
        }

        static ValueAnimator start(ViewGroup parent, View going) {
            if (running != null) running.finish();
            Swap swap = new Swap(parent, going);
            running = swap;
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.addUpdateListener(swap);
            animator.addListener(swap);
            return animator;
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animator) {
            if (done) return;
            live = animator;
            float t = animator.getAnimatedFraction();
            // The first call comes while TikTok is still switching, before the new page shows.
            if (frames++ == 0) return;
            if (coming == null) {
                coming = comingPage();
                int now = currentTab();
                side = now >= 0 && lastTab >= 0 && now < lastTab ? -1 : 1;
                lastTab = now;
                if (coming == null) {
                    // Not a switch between pages (the frame hid one for something else): just go.
                    going.setAlpha(0f);
                    return;
                }
                hideSurfaces(going);
            }
            // The page going is drawn over the one coming. It slides off and fades, and the new
            // page stays opaque underneath, so nothing behind the two (black under TikTok's tabs)
            // ever shows through. The new page settles from a little larger, moving with the
            // swap: it is shifted by exactly the part it overflows, so its edge never comes in.
            float width = Math.max(going.getWidth(), coming.getWidth());
            float leave = Motion.part(t, 0f, 0.7f, Motion.LEAVE);
            going.setTranslationX(-side * width * Motion.TAB_TRAVEL * leave);
            going.setAlpha(1f - Motion.part(t, 0f, 0.6f, Motion.STANDARD));
            float rest = 1f - Motion.part(t, 0f, 1f, Motion.ARRIVE);
            coming.setAlpha(1f);
            coming.setScaleX(1f + 2f * Motion.TAB_SETTLE * rest);
            coming.setScaleY(1f + 2f * Motion.TAB_SETTLE * rest);
            coming.setTranslationX(side * width * Motion.TAB_SETTLE * rest);
        }

        private void hideSurfaces(View view) {
            if (view instanceof SurfaceView) {
                if (view.getVisibility() == View.VISIBLE) {
                    view.setVisibility(View.INVISIBLE);
                    hiddenSurfaces.add(view);
                }
                return;
            }
            if (!(view instanceof ViewGroup)) return;
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.getVisibility() == View.VISIBLE) hideSurfaces(child);
            }
        }

        private View comingPage() {
            ViewGroup frame = parent.get();
            if (frame == null) return null;
            for (int i = frame.getChildCount() - 1; i >= 0; i--) {
                View child = frame.getChildAt(i);
                if (child != going && child.getVisibility() == View.VISIBLE) return child;
            }
            return null;
        }

        @Override
        public void onAnimationEnd(Animator animator) {
            finish();
        }

        /**
         * Puts both pages back as TikTok left them. Called at the end, or when a new switch starts,
         * which also ends this one's animator so the page it hid stops being drawn at once.
         */
        void finish() {
            if (done) return;
            done = true;
            going.setTranslationX(0f);
            going.setAlpha(1f);
            for (View surface : hiddenSurfaces) {
                if (surface.getVisibility() == View.INVISIBLE) surface.setVisibility(View.VISIBLE);
            }
            hiddenSurfaces.clear();
            if (coming != null) {
                coming.setTranslationX(0f);
                coming.setScaleX(1f);
                coming.setScaleY(1f);
                coming.setAlpha(1f);
            }
            if (running == this) running = null;
            ValueAnimator animator = live;
            live = null;
            if (animator != null && animator.isRunning()) animator.end();
        }
    }
}
