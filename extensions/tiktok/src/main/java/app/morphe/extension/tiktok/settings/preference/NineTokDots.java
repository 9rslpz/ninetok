/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.settings.preference;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * The settings screen's background: the page colour with a fine grid of faint dots over it, in
 * the manner of Nothing OS. It sits on the screen's root, so the grid stays still while the
 * transparent rows scroll over it.
 */
public final class NineTokDots extends Drawable {
    private static final float SPACING_DP = 14f;
    private static final float RADIUS_DP = 0.8f;
    /** Dot opacity: barely there on cream, a touch more on graphite where it reads fainter. */
    private static final int LIGHT_ALPHA = 0x17;
    private static final int DARK_ALPHA = 0x1C;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float spacing;
    private final float radius;

    public NineTokDots(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        spacing = SPACING_DP * density;
        radius = Math.max(0.6f, RADIUS_DP * density);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        paint.setColor(SettingsUi.background());
        canvas.drawRect(bounds, paint);

        int alpha = SettingsUi.isDarkMode() ? DARK_ALPHA : LIGHT_ALPHA;
        paint.setColor((SettingsUi.textPrimary() & 0x00FFFFFF) | (alpha << 24));
        // Centred across the width, so both edges keep the same margin of dots.
        float left = bounds.left + (bounds.width() % spacing) / 2f + spacing / 2f;
        for (float y = bounds.top + spacing / 2f; y < bounds.bottom; y += spacing) {
            for (float x = left; x < bounds.right; x += spacing) {
                canvas.drawCircle(x, y, radius, paint);
            }
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
