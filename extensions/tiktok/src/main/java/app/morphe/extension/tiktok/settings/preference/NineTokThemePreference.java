/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.settings.preference;

import android.app.Activity;
import android.content.Context;
import android.preference.ListPreference;
import android.view.View;

import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * The settings theme: TikTok's own, light, dark gray or OLED black. A new choice repaints the
 * whole screen by recreating it, since every row took its colours as it was drawn.
 */
@SuppressWarnings("deprecation")
public final class NineTokThemePreference extends ListPreference {
    private static final String[] LABELS = {"Follow TikTok", "Light", "Dark gray", "OLED black"};
    private static final String[] VALUES = {"tiktok", "light", "dark", "oled"};

    public NineTokThemePreference(Context context) {
        super(context);
        String title = L10n.t(context, "Theme");
        setTitle(title);
        setDialogTitle(title);
        setKey(Settings.NINETOK_THEME.key);
        String[] labels = new String[LABELS.length];
        for (int i = 0; i < LABELS.length; i++) labels[i] = L10n.t(context, LABELS[i]);
        setEntries(labels);
        setEntryValues(VALUES);
        setValue(Settings.NINETOK_THEME.savedValue());
        setSummary("%s");
        setNegativeButtonText(L10n.t(context, "Cancel"));
    }

    @Override
    protected void showDialog(android.os.Bundle state) {
        super.showDialog(state);
        SettingsUi.styleStandardAlertDialog((android.app.AlertDialog) getDialog());
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        app.morphe.extension.tiktok.Utils.setTitleAndSummaryColor(view);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        String before = getValue();
        super.onDialogClosed(positiveResult);
        String after = getValue();
        if (after == null || after.equals(before)) return;
        Settings.NINETOK_THEME.save(after);
        Context context = getContext();
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            activity.getWindow().getDecorView().post(activity::recreate);
        }
    }
}
