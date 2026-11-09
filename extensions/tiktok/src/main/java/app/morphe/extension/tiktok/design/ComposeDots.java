/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.design;

import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.os.Build;
import android.view.View;

import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.shared.Logger;

/**
 * The dot grid on TikTok's Compose screens (Settings and privacy and others). Compose paints a
 * screen inside one view, with no background of its own to put the grid on, so the grid goes
 * over the finished picture as a shader that only touches the surfaces: plain near-neutral pixels
 * dark in dark mode or near-white in light mode. Text, icons, pictures and accents keep every
 * pixel. The dots sit on the window's grid, as on the other screens, so they hold still while
 * the list scrolls under them. Android 13 and newer; older phones keep the plain surfaces.
 */
final class ComposeDots {
    private static final String SHADER =
            "uniform shader content;\n"
            + "uniform float2 origin;\n"
            + "uniform float spacing;\n"
            + "uniform float radius;\n"
            + "uniform half4 darkDot;\n"
            + "uniform half4 lightDot;\n"
            + "half4 main(float2 p) {\n"
            + "  half4 c = content.eval(p);\n"
            + "  if (c.a < 0.99) return c;\n"
            + "  half hi = max(c.r, max(c.g, c.b));\n"
            + "  half lo = min(c.r, min(c.g, c.b));\n"
            + "  if (hi - lo > 0.04) return c;\n"
            + "  half lum = c.r * 0.299 + c.g * 0.587 + c.b * 0.114;\n"
            + "  half4 dotColor;\n"
            + "  if (lum < 0.32) dotColor = darkDot; else if (lum > 0.86) dotColor = lightDot; else return c;\n"
            + "  float2 q = mod(p + origin, spacing) - spacing * 0.5;\n"
            + "  float a = clamp(radius + 0.5 - length(q), 0.0, 1.0) * dotColor.a;\n"
            + "  return half4(mix(c.rgb, dotColor.rgb, a), c.a);\n"
            + "}\n";

    /**
     * The Compose views that have the grid. It goes on once and stays: replacing a render effect
     * on a Compose screen while TikTok changed its theme left the screen frozen until it was
     * closed. The shader picks light or dark dots from each pixel, so a theme change needs nothing.
     */
    private static final Map<View, Boolean> APPLIED = new WeakHashMap<>();

    private ComposeDots() {
    }

    static boolean isCompose(View view) {
        return view.getClass().getName().equals("androidx.compose.ui.platform.AndroidComposeView");
    }

    /** Puts the grid on {@code view} once, the first time it is big enough to be a screen. */
    static void apply(View view, boolean on) {
        if (Build.VERSION.SDK_INT < 33) return;
        try {
            if (!on || APPLIED.containsKey(view) || !view.isLaidOut()) return;
            APPLIED.put(view, Boolean.TRUE);
            int[] at = new int[2];
            view.getLocationInWindow(at);
            float density = view.getResources().getDisplayMetrics().density;
            RuntimeShader shader = new RuntimeShader(SHADER);
            shader.setFloatUniform("origin", at[0], at[1]);
            shader.setFloatUniform("spacing", 14 * density);
            shader.setFloatUniform("radius", Math.max(0.6f, 0.8f * density));
            shader.setFloatUniform("darkDot", 0xFA / 255f, 0xF9 / 255f, 0xF5 / 255f, 0x1C / 255f);
            shader.setFloatUniform("lightDot", 0x14 / 255f, 0x14 / 255f, 0x13 / 255f, 0x17 / 255f);
            view.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"));
        } catch (Throwable error) {
            Logger.printException(() -> "Could not lay the dot grid on a Compose screen", error);
        }
    }
}
