/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.repost;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;

import app.morphe.extension.shared.GlobalLayoutHook;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.motion.Motion;
import app.morphe.extension.tiktok.navigation.FrontWindow;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.preference.NineTokType;

/**
 * A small "Repost" button that comes up next to the author's name once you like a video, as
 * older TikTok builds had. Tapping it reposts the video the way the share panel's Repost does,
 * and tapping it again takes the repost back.
 *
 * <p>The like itself is left to TikTok: the patch only reports when a video's like turns on or
 * off (from {@code Aweme.setUserDigg} and {@code Aweme.setLike}). The button goes into the feed
 * page that shows that author's name, beside the name, and leaves with the page or the like.
 *
 * <p>The repost goes through the same service call as the share panel's Repost, whose class,
 * holder field and two methods the patch reads from the share panel and writes into
 * {@link #targets()}, so nothing here names TikTok's obfuscated classes.
 */
public final class RepostPill {
    /** Space between the button and the name under it. */
    private static final int GAP_DP = 6;
    /** How often the button checks the repost is still there while it is on screen. */
    private static final long RECHECK_MS = 1500;
    /** A view tag marking the pill, so a layout pass can tell it from TikTok's views. */
    private static final int PILL_TAG = 0x7f7f4021;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final GlobalLayoutHook LAYOUT = new GlobalLayoutHook();
    private static final FrontWindow FRONT = new FrontWindow(true, new FrontWindow.Listener() {
        @Override public void onFront(Activity activity) {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root != null) LAYOUT.install(root, RepostPill::follow);
        }

        @Override public void onGone(Activity activity) {
            LAYOUT.detach();
            remove(false);
        }
    });

    private static Method isLike;
    /** The pill on screen, the name it sits beside, and the video it reposts. */
    private static WeakReference<TextView> pill = new WeakReference<>(null);
    private static WeakReference<TextView> name = new WeakReference<>(null);
    private static WeakReference<Object> video = new WeakReference<>(null);
    private static String nameText;
    private static boolean reposted;
    private static boolean busy;

    private RepostPill() {
    }

    /** From the patched main activity's onCreate; touches no view. */
    public static void install(Context context) {
        try {
            if (context instanceof Activity) FRONT.add((Activity) context);
        } catch (Throwable error) {
            Logger.printException(() -> "Could not follow the feed for the repost button", error);
        }
    }

    /**
     * Filled in by the patch: "holder class|holder field|publish method|undo method|repost list
     * method|account class|account method", what the share panel's Repost uses. Empty when the
     * patch could not find them.
     */
    static String targets() {
        return "";
    }

    /** From {@code Aweme.setUserDigg(int)}, before it sets the value: 1 is liked. */
    public static void onUserDigg(Object aweme, int digg) {
        onLike(aweme, digg == 1);
    }

    /** From {@code Aweme.setLike(boolean)}, before it sets the value. */
    public static void onLike(Object aweme, boolean liked) {
        try {
            if (aweme == null || !Settings.REPOST_AFTER_LIKE.get()) return;
            boolean was = liked(aweme);
            if (liked == was) return;
            MAIN.post(() -> {
                if (liked) show(aweme);
                else if (video.get() == aweme) remove(true);
            });
        } catch (Throwable error) {
            Logger.printException(() -> "Could not follow a like for the repost button", error);
        }
    }

    private static boolean liked(Object aweme) throws Exception {
        if (isLike == null) isLike = aweme.getClass().getMethod("isLike");
        return Boolean.TRUE.equals(isLike.invoke(aweme));
    }

    private static void show(Object aweme) {
        try {
            Activity activity = FRONT.get();
            if (activity == null || activity.isFinishing()) return;
            if (video.get() == aweme && pill.get() != null && pill.get().isAttachedToWindow()) return;
            String nickname = nickname(aweme);
            if (nickname == null || nickname.isEmpty()) return;
            View root = activity.findViewById(android.R.id.content);
            TextView label = visibleName(root, nickname);
            if (label == null) return;
            FrameLayout host = pageAbove(label, root);
            if (host == null) return;

            remove(false);
            TextView button = makePill(activity);
            host.addView(button, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.START));
            pill = new WeakReference<>(button);
            name = new WeakReference<>(label);
            video = new WeakReference<>(aweme);
            nameText = nickname;
            Boolean now = repostedNow(aweme);
            reposted = now != null && now;
            busy = false;
            style(button);
            place();
            recheckLater();
            button.setAlpha(0f);
            button.setScaleX(0.8f);
            button.setScaleY(0.8f);
            button.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(260).setInterpolator(Motion.ARRIVE).start();
        } catch (Throwable error) {
            Logger.printException(() -> "Could not show the repost button", error);
        }
    }

    private static String nickname(Object aweme) throws Exception {
        Object author = aweme.getClass().getMethod("getAuthor").invoke(aweme);
        if (author == null) return null;
        Object nick = author.getClass().getMethod("getNickname").invoke(author);
        return nick == null ? null : nick.toString().trim();
    }

    /** The shown text view reading the author's name that has the most of itself on screen. */
    private static TextView visibleName(View root, String nickname) {
        if (root == null) return null;
        TextView best = null;
        int bestArea = 0;
        Rect rect = new Rect();
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            View view = queue.poll();
            if (view.getVisibility() != View.VISIBLE) continue;
            if (view instanceof TextView && view.getTag(PILL_TAG) == null) {
                CharSequence text = ((TextView) view).getText();
                if (text != null && nickname.contentEquals(text.toString().trim())
                        && view.isShown() && view.getGlobalVisibleRect(rect)) {
                    int area = rect.width() * rect.height();
                    if (area > bestArea) {
                        bestArea = area;
                        best = (TextView) view;
                    }
                }
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) queue.add(group.getChildAt(i));
            }
        }
        return best;
    }

    /** The feed page holding the name: its first frame ancestor at least half the screen tall. */
    private static FrameLayout pageAbove(View label, View root) {
        int half = root.getHeight() / 2;
        for (Object parent = label.getParent(); parent instanceof View && parent != root;
             parent = ((View) parent).getParent()) {
            if (parent instanceof FrameLayout && ((View) parent).getHeight() >= half) {
                return (FrameLayout) parent;
            }
        }
        return null;
    }

    /** On each layout pass: keep the pill beside the name, or take it away with its page. */
    private static void follow() {
        try {
            TextView button = pill.get();
            if (button == null) return;
            TextView label = name.get();
            Object aweme = video.get();
            boolean gone = label == null || aweme == null || !label.isAttachedToWindow()
                    || !button.isAttachedToWindow() || !Settings.REPOST_AFTER_LIKE.get()
                    || label.getText() == null || !label.getText().toString().trim().equals(nameText);
            if (gone) {
                remove(false);
                return;
            }
            place();
            recheck();
        } catch (Throwable error) {
            Logger.printException(() -> "Could not keep the repost button by the name", error);
        }
    }

    /**
     * Above the name, lined up with where its text starts. Beside it, a long name pushed the
     * button over the video's buttons; above, a name of any length leaves it where it is.
     */
    private static void place() {
        TextView button = pill.get();
        TextView label = name.get();
        if (button == null || label == null || !(button.getParent() instanceof View)) return;
        View host = (View) button.getParent();
        if (button.getMeasuredHeight() == 0) {
            int any = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            button.measure(any, any);
        }
        int height = button.getHeight() > 0 ? button.getHeight() : button.getMeasuredHeight();
        int[] at = new int[2];
        int[] hostAt = new int[2];
        label.getLocationInWindow(at);
        host.getLocationInWindow(hostAt);
        float textStart = label.getPaddingLeft();
        if (label.getLayout() != null && label.getLayout().getLineCount() > 0) {
            textStart += label.getLayout().getLineLeft(0);
        }
        float x = at[0] - hostAt[0] + textStart;
        float y = at[1] - hostAt[1] + label.getPaddingTop() - height - dp(label.getContext(), GAP_DP);
        button.setPivotX(0f);
        button.setPivotY(height);
        if (button.getTranslationX() != x) button.setTranslationX(x);
        if (button.getTranslationY() != y) button.setTranslationY(y);
    }

    private static TextView makePill(Context context) {
        TextView button = new TextView(context);
        button.setTag(PILL_TAG, Boolean.TRUE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        NineTokType.apply(button);
        button.setIncludeFontPadding(false);
        button.setGravity(Gravity.CENTER_VERTICAL);
        int side = dp(context, 9);
        int top = dp(context, 4);
        button.setPadding(side, top, side + dp(context, 1), top);
        button.setCompoundDrawablePadding(dp(context, 4));
        button.setTextColor(0xFFFFFFFF);
        button.setShadowLayer(dp(context, 2), 0, dp(context, 1), 0x40000000);
        button.setOnClickListener(view -> toggle());
        return button;
    }

    private static void style(TextView button) {
        Context context = button.getContext();
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(context, 999));
        if (reposted) {
            shape.setColor(0xE6D97757);
        } else {
            shape.setColor(0x33FFFFFF);
            shape.setStroke(Math.max(1, dp(context, 1) / 2), 0x59FFFFFF);
        }
        button.setBackground(shape);
        button.setText(reposted ? "Reposted" : "Repost");
        int size = dp(context, 12);
        RepostIcon icon = new RepostIcon(dp(context, 1) * 1.6f);
        icon.setBounds(0, 0, size, size);
        button.setCompoundDrawables(icon, null, null, null);
        button.setAlpha(busy ? 0.6f : 1f);
    }

    private static void toggle() {
        TextView button = pill.get();
        Object aweme = video.get();
        if (button == null || aweme == null || busy) return;
        try {
            String[] parts = targets().split("\\|");
            if (parts.length < 7) {
                Logger.printInfo(() -> "Repost button: the patch found no repost service in this build");
                return;
            }
            busy = true;
            style(button);
            // TikTok calls back when the repost (or its undo) went through; the publish has two
            // callbacks, the second one offering to add text to the repost, and both mean done.
            // Nothing calls back when it failed, so the button wakes up again after a while.
            final boolean[] answered = {false};
            Runnable done = () -> MAIN.post(() -> {
                if (answered[0]) return;
                answered[0] = true;
                busy = false;
                Object current = video.get();
                Boolean real = current == null ? null : repostedNow(current);
                reposted = real != null ? real : !reposted;
                TextView now = pill.get();
                if (now != null) {
                    style(now);
                    now.animate().scaleX(1.08f).scaleY(1.08f).setDuration(90)
                            .setInterpolator(Motion.STANDARD).withEndAction(() ->
                                    now.animate().scaleX(1f).scaleY(1f).setDuration(160)
                                            .setInterpolator(Motion.ARRIVE).start()).start();
                }
            });
            MAIN.postDelayed(() -> {
                if (answered[0]) return;
                answered[0] = true;
                busy = false;
                // No callback (TikTok's undo does not always call one): go by its record.
                Object current = video.get();
                Boolean real = current == null ? null : repostedNow(current);
                if (real != null) reposted = real;
                TextView now = pill.get();
                if (now != null) style(now);
            }, 3_000);
            if (reposted) undo(parts, aweme, done);
            else publish(parts, aweme, done);
        } catch (Throwable error) {
            busy = false;
            style(button);
            Logger.printException(() -> "Could not repost from the repost button", error);
        }
    }

    private static long lastCheck;
    private static final Runnable RECHECK = () -> {
        if (pill.get() == null) return;
        recheck();
        recheckLater();
    };

    private static void recheckLater() {
        MAIN.removeCallbacks(RECHECK);
        if (pill.get() != null) MAIN.postDelayed(RECHECK, RECHECK_MS);
    }

    /**
     * Brings the button in line with TikTok's own record of the repost, at most a few times a
     * second: a repost taken back from the share panel, or one the button's undo took back,
     * turns it back into "Repost".
     */
    private static void recheck() {
        long now = android.os.SystemClock.uptimeMillis();
        if (busy || now - lastCheck < 400) return;
        lastCheck = now;
        TextView button = pill.get();
        Object aweme = video.get();
        if (button == null || aweme == null) return;
        Boolean real = repostedNow(aweme);
        if (real == null || real == reposted) return;
        reposted = real;
        style(button);
    }

    /**
     * Whether you have reposted the video, as the share panel works it out: your account among
     * the reposts TikTok keeps for it. Null when TikTok has no list for it yet.
     */
    private static Boolean repostedNow(Object aweme) {
        try {
            String[] parts = targets().split("\\|");
            if (parts.length < 7) return null;
            Object service = service(parts);
            Object aid = aweme.getClass().getMethod("getAid").invoke(aweme);
            Object list = method(service, parts[4], 1).invoke(service, aid);
            if (list == null) return null;
            Object upvotes = list.getClass().getField("upvotes").get(list);
            if (!(upvotes instanceof java.util.List)) return false;
            String me = currentUserId(parts);
            if (me == null || me.isEmpty()) return null;
            for (Object upvote : (java.util.List<?>) upvotes) {
                if (upvote == null) continue;
                Object user = upvote.getClass().getField("user").get(upvote);
                if (user == null) continue;
                Object uid = user.getClass().getMethod("getUid").invoke(user);
                if (me.equals(uid)) return true;
            }
            return false;
        } catch (Throwable error) {
            Logger.printException(() -> "Could not read whether the video is reposted", error);
            return null;
        }
    }

    private static String currentUserId(String[] parts) throws Exception {
        Class<?> account = Class.forName(parts[5], false, RepostPill.class.getClassLoader());
        Method getter = account.getDeclaredMethod(parts[6]);
        getter.setAccessible(true);
        Object service = getter.invoke(null);
        if (service == null) return null;
        Object uid = service.getClass().getMethod("getCurUserId").invoke(service);
        return uid == null ? null : uid.toString();
    }

    private static Object service(String[] parts) throws Exception {
        Class<?> holder = Class.forName(parts[0], false, RepostPill.class.getClassLoader());
        Field field = holder.getDeclaredField(parts[1]);
        field.setAccessible(true);
        return field.get(null);
    }

    private static Method method(Object service, String name, int parameters) {
        for (Method method : service.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == parameters) return method;
        }
        throw new IllegalStateException("no " + name + " with " + parameters + " parameters");
    }

    private static Object mobParam(Object aweme) throws Exception {
        ClassLoader loader = RepostPill.class.getClassLoader();
        Object mob = Class.forName("com.ss.android.ugc.aweme.upvote.event.UpvotePublishMobParam", false, loader)
                .getDeclaredConstructor().newInstance();
        Object aid = aweme.getClass().getMethod("getAid").invoke(aweme);
        Object author = aweme.getClass().getMethod("getAuthor").invoke(aweme);
        Object uid = author == null ? null : author.getClass().getMethod("getUid").invoke(author);
        set(mob, "enterFrom", "homepage_hot");
        set(mob, "enterMethod", "share_panel");
        set(mob, "groupId", aid);
        set(mob, "authorId", uid);
        return mob;
    }

    private static void set(Object target, String field, Object value) {
        try {
            target.getClass().getField(field).set(target, value);
        } catch (Throwable ignored) {
            // A field a build dropped only loses that line of TikTok's own statistics.
        }
    }

    private static void publish(String[] parts, Object aweme, Runnable done) throws Exception {
        ClassLoader loader = RepostPill.class.getClassLoader();
        Object service = service(parts);
        Method call = method(service, parts[2], 14);
        Class<?> extraType = Class.forName("com.ss.android.ugc.aweme.upvote.event.UpvotePublishExtraData", false, loader);
        Class<?> awemeType = Class.forName("com.ss.android.ugc.aweme.feed.model.Aweme", false, loader);
        Object extra = extraType.getConstructor(awemeType, int.class).newInstance(aweme, 0);
        Object aid = aweme.getClass().getMethod("getAid").invoke(aweme);
        Class<?>[] types = call.getParameterTypes();
        Object[] args = new Object[14];
        args[3] = extra;
        args[4] = mobParam(aweme);
        args[5] = 0;
        args[6] = aid;
        args[11] = function(types[11], done);
        args[12] = function(types[12], done);
        args[13] = true;
        call.invoke(service, args);
    }

    private static void undo(String[] parts, Object aweme, Runnable done) throws Exception {
        Object service = service(parts);
        Method call = method(service, parts[3], 9);
        Class<?>[] types = call.getParameterTypes();
        Object[] args = new Object[9];
        args[0] = aweme.getClass().getMethod("getAid").invoke(aweme);
        args[1] = mobParam(aweme);
        args[2] = false;
        args[3] = function(types[3], done);
        args[8] = false;
        call.invoke(service, args);
    }

    /** A Kotlin function of TikTok's that runs {@code action} whatever it is handed. */
    private static Object function(Class<?> type, Runnable action) {
        if (!type.isInterface()) return null;
        InvocationHandler handler = (proxy, method, arguments) -> {
            switch (method.getName()) {
                case "equals":
                    return proxy == arguments[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "NINE’tok repost callback";
                default:
                    action.run();
                    return null;
            }
        };
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static void remove(boolean animate) {
        MAIN.removeCallbacks(RECHECK);
        TextView button = pill.get();
        pill = new WeakReference<>(null);
        name = new WeakReference<>(null);
        video = new WeakReference<>(null);
        nameText = null;
        if (button == null || !(button.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) button.getParent();
        if (!animate || !button.isAttachedToWindow()) {
            parent.removeView(button);
            return;
        }
        button.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(150)
                .setInterpolator(Motion.LEAVE).setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        parent.removeView(button);
                    }
                }).start();
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** Two arrows chasing each other round a rounded square, in the text's colour. */
    private static final class RepostIcon extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF box = new RectF();

        RepostIcon(float stroke) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(0xFFFFFFFF);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float w = b.width();
            float h = b.height();
            float inset = paint.getStrokeWidth();
            box.set(b.left + inset, b.top + h * 0.22f, b.right - inset, b.bottom - h * 0.22f);
            float r = box.height() / 2.2f;
            float head = w * 0.18f;
            path.reset();
            // Top run, left to right, ending in an arrowhead.
            path.moveTo(box.left, box.centerY());
            path.lineTo(box.left, box.top + r);
            path.quadTo(box.left, box.top, box.left + r, box.top);
            path.lineTo(box.right - head * 0.4f, box.top);
            path.moveTo(box.right - head * 1.3f, box.top - head);
            path.lineTo(box.right - head * 0.3f, box.top);
            path.lineTo(box.right - head * 1.3f, box.top + head);
            // Bottom run, right to left, ending in an arrowhead.
            path.moveTo(box.right, box.centerY());
            path.lineTo(box.right, box.bottom - r);
            path.quadTo(box.right, box.bottom, box.right - r, box.bottom);
            path.lineTo(box.left + head * 0.4f, box.bottom);
            path.moveTo(box.left + head * 1.3f, box.bottom - head);
            path.lineTo(box.left + head * 0.3f, box.bottom);
            path.lineTo(box.left + head * 1.3f, box.bottom + head);
            canvas.drawPath(path, paint);
        }

        @Override public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override public void setColorFilter(ColorFilter filter) {
            paint.setColorFilter(filter);
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
