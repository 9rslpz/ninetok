/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.design;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.shared.GlobalLayoutHook;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * Three themes on TikTok's own Settings and privacy > Display page: Light, Grey and Black.
 *
 * <p>TikTok's Appearance card has two columns, Light and Dark, each a preview, a name and a
 * radio button. Dark becomes Grey (NINE’tok's warm graphite) and a third column, Black, comes in
 * beside it; all three get the same radio buttons, drawn here, so Grey and Black can show which
 * of the two dark themes is on. Light and dark stay TikTok's own switch: a tap on Light, Grey or
 * Black presses TikTok's radio button for light or dark, and Grey and Black also choose which dark
 * theme it shows ({@link ThemeSwitch}). With "Use device settings" on, TikTok's buttons are off
 * and so are these, but Grey and Black still pick the dark theme the phone's dark mode will get.
 */
public final class DisplayThemes {
    /** TikTok 47.4.3's ids: the card's row of columns, each column and its radio button. */
    private static final String ROW = "z7_";
    private static final String LIGHT = "z7c";
    private static final String LIGHT_RADIO = "z7d";
    private static final String DARK = "z7a";
    private static final String DARK_RADIO = "z7b";
    /** Marks the Black column and the radio buttons drawn here. */
    private static final int OURS = 0x7f7f4041;

    private static final int CREAM = 0xFFFAF9F5;
    private static final int GRAPHITE = 0x1F1E1D;
    private static final int ACCENT = 0xFFD97757;

    private static final Map<Activity, GlobalLayoutHook> HOOKS = new WeakHashMap<>();
    private static Application watching;

    private static final Application.ActivityLifecycleCallbacks WINDOWS = new Application.ActivityLifecycleCallbacks() {
        @Override public void onActivityResumed(Activity activity) {
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

    private DisplayThemes() {
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
            Logger.printException(() -> "Could not watch for TikTok's Display page", error);
        }
    }

    private static void watch(Activity activity) {
        try {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            GlobalLayoutHook hook = HOOKS.get(activity);
            if (hook == null) {
                hook = new GlobalLayoutHook();
                HOOKS.put(activity, hook);
            }
            hook.install(root, () -> apply(activity));
            apply(activity);
        } catch (Throwable error) {
            Logger.printException(() -> "Could not watch a window for TikTok's Display page", error);
        }
    }

    private static View byName(Activity activity, String name) {
        int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
        return id == 0 ? null : activity.findViewById(id);
    }

    /** On each layout pass of a window that shows the Appearance card. */
    private static void apply(Activity activity) {
        try {
            View rowView = byName(activity, ROW);
            View lightView = byName(activity, LIGHT);
            View darkView = byName(activity, DARK);
            View lightRadio = byName(activity, LIGHT_RADIO);
            View darkRadio = byName(activity, DARK_RADIO);
            if (!(rowView instanceof LinearLayout) || !(lightView instanceof LinearLayout)
                    || !(darkView instanceof LinearLayout) || lightRadio == null || darkRadio == null) return;
            LinearLayout row = (LinearLayout) rowView;
            LinearLayout light = (LinearLayout) lightView;
            LinearLayout dark = (LinearLayout) darkView;
            if (light.getParent() != row || dark.getParent() != row) return;

            LinearLayout black = findBlack(row);
            if (black == null) black = build(activity, row, light, dark, lightRadio, darkRadio);
            if (black == null) return;
            refresh(activity, light, dark, black, lightRadio, darkRadio);
        } catch (Throwable error) {
            Logger.printException(() -> "Could not add Black to TikTok's Display page", error);
        }
    }

    private static LinearLayout findBlack(LinearLayout row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (child instanceof LinearLayout && child.getTag(OURS) != null) return (LinearLayout) child;
        }
        return null;
    }

    /** The Black column, made like TikTok's Dark one, and the three columns sharing the row. */
    private static LinearLayout build(Activity activity, LinearLayout row, LinearLayout light, LinearLayout dark,
                                      View lightRadio, View darkRadio) {
        ImageView darkImage = first(dark, ImageView.class);
        TextView darkLabel = first(dark, TextView.class);
        if (darkImage == null || darkLabel == null || darkImage.getDrawable() == null) return null;

        LinearLayout black = new LinearLayout(activity);
        black.setTag(OURS, Boolean.TRUE);
        black.setOrientation(dark.getOrientation());
        black.setGravity(dark.getGravity());
        black.setPadding(dark.getPaddingLeft(), dark.getPaddingTop(), dark.getPaddingRight(), dark.getPaddingBottom());
        black.setClickable(true);

        ImageView image = new ImageView(activity);
        Drawable.ConstantState state = darkImage.getDrawable().getConstantState();
        image.setImageDrawable(state != null ? state.newDrawable(activity.getResources()).mutate() : darkImage.getDrawable());
        image.setScaleType(darkImage.getScaleType());
        image.setAdjustViewBounds(darkImage.getAdjustViewBounds());
        black.addView(image, copy(darkImage));

        TextView label = new TextView(activity);
        label.setText("Black");
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, darkLabel.getTextSize());
        label.setTypeface(darkLabel.getTypeface());
        label.setTextColor(darkLabel.getTextColors());
        label.setGravity(darkLabel.getGravity());
        label.setIncludeFontPadding(darkLabel.getIncludeFontPadding());
        label.setLetterSpacing(darkLabel.getLetterSpacing());
        label.setPadding(darkLabel.getPaddingLeft(), darkLabel.getPaddingTop(),
                darkLabel.getPaddingRight(), darkLabel.getPaddingBottom());
        LinearLayout.LayoutParams labelParams = copy(darkLabel);
        if (darkLabel.getHeight() > 0) labelParams.height = darkLabel.getHeight();
        label.setGravity(android.view.Gravity.CENTER);
        black.addView(label, labelParams);

        Radio blackRadio = new Radio(activity);
        black.addView(blackRadio, radioParams(darkRadio));

        // TikTok's own buttons stay in place for their taps, hidden behind the shared ones.
        addRadio(light, lightRadio);
        addRadio(dark, darkRadio);

        // Three columns across the row instead of two.
        int side = Math.round(8 * activity.getResources().getDisplayMetrics().density);
        row.setPadding(side, row.getPaddingTop(), side, row.getPaddingBottom());
        row.addView(black, row.indexOfChild(dark) + 1);
        for (View column : new View[]{light, dark, black}) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            ViewGroup.LayoutParams old = column == black ? dark.getLayoutParams() : column.getLayoutParams();
            if (old instanceof ViewGroup.MarginLayoutParams) {
                params.topMargin = ((ViewGroup.MarginLayoutParams) old).topMargin;
                params.bottomMargin = ((ViewGroup.MarginLayoutParams) old).bottomMargin;
            }
            column.setLayoutParams(params);
        }

        View.OnClickListener chooseLight = view -> choose(activity, null, lightRadio, darkRadio);
        View.OnClickListener chooseGrey = view -> choose(activity, ThemeSwitch.GRAY, lightRadio, darkRadio);
        View.OnClickListener chooseBlack = view -> choose(activity, ThemeSwitch.OLED, lightRadio, darkRadio);
        light.setOnClickListener(chooseLight);
        ours(light).setOnClickListener(chooseLight);
        dark.setOnClickListener(chooseGrey);
        ours(dark).setOnClickListener(chooseGrey);
        black.setOnClickListener(chooseBlack);
        blackRadio.setOnClickListener(chooseBlack);
        return black;
    }

    /** One of the shared radio buttons beside TikTok's, which is kept for its taps but not shown. */
    private static void addRadio(LinearLayout column, View theirs) {
        Radio radio = new Radio(column.getContext());
        column.addView(radio, column.indexOfChild(theirs) + 1, radioParams(theirs));
        theirs.setVisibility(View.GONE);
    }

    private static Radio ours(LinearLayout column) {
        for (int i = 0; i < column.getChildCount(); i++) {
            if (column.getChildAt(i) instanceof Radio) return (Radio) column.getChildAt(i);
        }
        return null;
    }

    /** TikTok's radio button's place, with its size set: drawn here, it has none of its own. */
    private static LinearLayout.LayoutParams radioParams(View theirs) {
        LinearLayout.LayoutParams params = copy(theirs);
        int fallback = Math.round(24 * theirs.getResources().getDisplayMetrics().density);
        int width = theirs.getWidth() > 0 ? theirs.getWidth() : fallback;
        int height = theirs.getHeight() > 0 ? theirs.getHeight() : fallback;
        if (params.width <= 0) params.width = width;
        if (params.height <= 0) params.height = height;
        return params;
    }

    private static LinearLayout.LayoutParams copy(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params instanceof LinearLayout.LayoutParams) return new LinearLayout.LayoutParams((LinearLayout.LayoutParams) params);
        if (params instanceof ViewGroup.MarginLayoutParams) return new LinearLayout.LayoutParams((ViewGroup.MarginLayoutParams) params);
        return new LinearLayout.LayoutParams(params);
    }

    private static <T extends View> T first(ViewGroup group, Class<T> type) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (type.isInstance(child) && child.getTag(OURS) == null && !(child instanceof Radio)) return type.cast(child);
        }
        return null;
    }

    private static boolean dark(Context context) {
        return TikTokTheme.isDark(context);
    }

    /**
     * Light (style null), Grey or Black. Light and dark go through TikTok's own buttons; in dark
     * mode already, a new dark theme repaints the open screens itself.
     */
    private static void choose(Activity activity, String style, View lightRadio, View darkRadio) {
        try {
            boolean manual = lightRadio.isEnabled();
            boolean darkNow = dark(activity);
            // The dark theme is saved first, so every screen built after this comes up in it.
            boolean newDark = style != null && ThemeSwitch.save(activity, style);
            boolean wantDark = style != null;
            if (manual && wantDark != darkNow) {
                // TikTok's own switch for light or dark, then the feed, built fresh.
                (wantDark ? darkRadio : lightRadio).performClick();
                ThemeSwitch.restartToFeedLater(activity);
            } else if (newDark && darkNow) {
                ThemeSwitch.restartToFeedLater(activity);
            } else {
                apply(activity);
            }
        } catch (Throwable error) {
            Logger.printException(() -> "Could not change the theme", error);
        }
    }

    /** Names, previews and buttons after the current theme, on every pass. */
    private static void refresh(Activity activity, LinearLayout light, LinearLayout dark, LinearLayout black,
                                View lightRadio, View darkRadio) {
        TextView darkLabel = first(dark, TextView.class);
        if (darkLabel != null && !"Grey".contentEquals(darkLabel.getText())) darkLabel.setText("Grey");

        ImageView lightImage = first(light, ImageView.class);
        if (lightImage != null && lightImage.getColorFilter() == null) {
            lightImage.setColorFilter(new LightingColorFilter(CREAM & 0xFFFFFF, 0));
        }
        ImageView darkImage = first(dark, ImageView.class);
        if (darkImage != null && darkImage.getColorFilter() == null) {
            darkImage.setColorFilter(new LightingColorFilter(0xFFFFFF, GRAPHITE));
        }

        boolean manual = lightRadio.isEnabled();
        boolean darkNow = dark(activity);
        boolean oled = ThemeSwitch.oled();
        Radio lightButton = ours(light);
        Radio darkButton = ours(dark);
        Radio blackButton = ours(black);
        if (lightButton == null || darkButton == null || blackButton == null) return;
        lightButton.set(!darkNow, manual, darkNow);
        // With the phone deciding light or dark, Grey and Black still show the dark theme chosen.
        darkButton.set(manual ? darkNow && !oled : !oled, true, darkNow);
        blackButton.set(manual ? darkNow && oled : oled, true, darkNow);
        if (lightRadio.getVisibility() != View.GONE) lightRadio.setVisibility(View.GONE);
        if (darkRadio.getVisibility() != View.GONE) darkRadio.setVisibility(View.GONE);
    }

    /** The round radio button the three columns share: a clay disc with a white dot when on. */
    static final class Radio extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean checked;
        private boolean usable = true;
        private boolean onDark;

        Radio(Context context) {
            super(context);
            setTag(OURS, Boolean.TRUE);
            setClickable(true);
        }

        void set(boolean checked, boolean usable, boolean onDark) {
            if (this.checked == checked && this.usable == usable && this.onDark == onDark) return;
            this.checked = checked;
            this.usable = usable;
            this.onDark = onDark;
            setAlpha(usable ? 1f : 0.4f);
            setContentDescription(checked ? "Selected" : "Not selected");
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float size = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(),
                    getHeight() - getPaddingTop() - getPaddingBottom());
            float cx = getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight()) / 2f;
            float cy = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom()) / 2f;
            float density = getResources().getDisplayMetrics().density;
            float radius = Math.min(size / 2f, 11 * density);
            if (checked) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ACCENT);
                canvas.drawCircle(cx, cy, radius, paint);
                paint.setColor(0xFFFFFFFF);
                canvas.drawCircle(cx, cy, radius * 0.42f, paint);
            } else {
                float stroke = 2 * density;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(stroke);
                paint.setColor(onDark ? 0x80FAF9F5 : 0x66141413);
                canvas.drawCircle(cx, cy, radius - stroke / 2f, paint);
            }
        }
    }
}
