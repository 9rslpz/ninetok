/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.settings.preference;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.TransformationMethod;
import android.text.style.MetricAffectingSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Locale;

/**
 * The settings screen's type: Manrope for the text, and the Yaroslav Hand 14 handwriting for the
 * letters NINE wherever the name appears. The fonts are assets the Settings patch copies into the
 * APK; a view keeps the system font when one of them can't be read.
 */
public final class NineTokType {
    private static final String FONT_DIR = "ninetok/fonts/";
    private static final String BRAND = "NINE";
    /** The handwriting is drawn small and wide beside Manrope; these even it out. */
    private static final float HAND_SCALE = 1.35f;
    private static final float HAND_LETTER_SPACING = -0.18f;

    private static Typeface regular;
    private static Typeface medium;
    private static Typeface semibold;
    private static Typeface hand;
    private static boolean loaded;

    private NineTokType() {
    }

    /** Sets the settings font on every text view under {@code view}, keeping each one's weight. */
    public static void applyTree(View view) {
        if (view instanceof TextView) {
            apply((TextView) view);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyTree(group.getChildAt(i));
        }
    }

    /** The settings font for one view, and the handwritten NINE if its text carries the name. */
    public static void apply(TextView view) {
        if (view == null || view instanceof EditText) return;
        Typeface current = view.getTypeface();
        if (current == Typeface.MONOSPACE) return;
        load(view.getContext());
        Typeface sans = sansFor(current);
        if (sans != null && sans != current) view.setTypeface(sans);
        brand(view);
    }

    private static synchronized void load(Context context) {
        if (loaded) return;
        loaded = true;
        regular = font(context, "sans-regular.ttf");
        medium = font(context, "sans-medium.ttf");
        semibold = font(context, "sans-semibold.ttf");
        hand = font(context, "hand.ttf");
    }

    private static Typeface font(Context context, String name) {
        try {
            return Typeface.createFromAsset(context.getAssets(), FONT_DIR + name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The Manrope weight nearest to what the view asked for; one of ours is kept as it is. */
    private static Typeface sansFor(Typeface current) {
        if (current != null && (current == regular || current == medium || current == semibold)) return current;
        if (current != null && current.isBold()) return semibold;
        if (current != null && Build.VERSION.SDK_INT >= 28) {
            int weight = current.getWeight();
            if (weight >= 600) return semibold;
            if (weight >= 500) return medium;
        }
        return regular;
    }

    private static void brand(TextView view) {
        if (hand == null) return;
        CharSequence text = view.getText();
        if (text == null || text.length() < BRAND.length()) return;
        boolean allCaps = isAllCaps(view);
        String shown = allCaps ? text.toString().toUpperCase(Locale.ROOT) : text.toString();
        if (!shown.contains(BRAND)) return;
        if (!allCaps && text instanceof Spanned
                && ((Spanned) text).getSpans(0, text.length(), HandSpan.class).length > 0) return;

        SpannableString branded = new SpannableString(allCaps ? shown : text);
        for (int i = shown.indexOf(BRAND); i >= 0; i = shown.indexOf(BRAND, i + BRAND.length())) {
            branded.setSpan(new HandSpan(hand), i, i + BRAND.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        // Android's all-caps transformation drops spans, so the text carries its own capitals.
        if (allCaps) view.setAllCaps(false);
        view.setText(branded);
    }

    private static boolean isAllCaps(TextView view) {
        if (Build.VERSION.SDK_INT >= 28) return view.isAllCaps();
        TransformationMethod method = view.getTransformationMethod();
        return method != null && method.getClass().getName().contains("AllCaps");
    }

    /** NINE in the handwriting, scaled up and drawn closer together to sit with Manrope. */
    private static final class HandSpan extends MetricAffectingSpan {
        private final Typeface typeface;

        HandSpan(Typeface typeface) {
            this.typeface = typeface;
        }

        @Override
        public void updateDrawState(TextPaint paint) {
            style(paint);
        }

        @Override
        public void updateMeasureState(TextPaint paint) {
            style(paint);
        }

        private void style(TextPaint paint) {
            paint.setTypeface(typeface);
            paint.setTextSize(paint.getTextSize() * HAND_SCALE);
            paint.setLetterSpacing(paint.getLetterSpacing() + HAND_LETTER_SPACING);
        }
    }
}
