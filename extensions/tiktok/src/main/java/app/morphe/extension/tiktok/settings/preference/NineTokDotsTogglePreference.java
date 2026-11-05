/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.settings.preference;

import android.content.Context;

import app.morphe.extension.tiktok.settings.Settings;

/** The switch for the dot grid behind the settings screen, which repaints it straight away. */
public final class NineTokDotsTogglePreference extends TogglePreference {
    public NineTokDotsTogglePreference(Context context) {
        super(context, "Dot grid background",
                "Show the faint grid of dots behind the NINE’tok settings.",
                Settings.NINETOK_DOT_GRID);
    }

    @Override
    protected void onClick() {
        super.onClick();
        NineTokDots.refreshAll();
    }
}
