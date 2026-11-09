/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.design;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * Which dark theme TikTok's dark mode shows: NINE’tok's warm Dark gray or OLED black. Light or
 * dark is TikTok's own choice (Settings and privacy > Display); this is the third option there.
 *
 * <p>One APK, both dark themes. The patch bakes Dark gray into TikTok's dark colours and writes
 * the OLED black copies into resource folders marked "round", a qualifier nothing in TikTok uses.
 * While OLED black is chosen every Resources object reports a round screen, so Android picks the
 * black copies; Dark gray leaves the configuration alone. Compose's dark palette asks
 * {@link #card(long)} instead, since it reads no resource.
 */
public final class ThemeSwitch {
    public static final String GRAY = "gray";
    public static final String OLED = "oled";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Set<Activity> LIVE = Collections.newSetFromMap(new WeakHashMap<>());
    private static Application watching;

    private ThemeSwitch() {
    }

    /** Whether OLED black is chosen. Dark gray when the setting can't be read yet. */
    public static boolean oled() {
        try {
            return OLED.equals(Settings.NINETOK_DARK_STYLE.get());
        } catch (Throwable error) {
            return false;
        }
    }

    /** Called by the patched Compose palette with the gray card colour (ARGB); returns the one to use. */
    public static long card(long gray) {
        return oled() ? 0xFF000000L : gray;
    }

    /**
     * Called by the patched Compose dark palette with each neutral dark it sets (ARGB, black
     * included): the same Grey or Black scale the resource side puts TikTok's dark themes on.
     */
    public static long composeDark(long argb) {
        int level = (int) (argb & 0xFF);
        int mapped = oled() ? blackTier(level) : grey(level);
        return 0xFF000000L | (mapped & 0xFFFFFFL);
    }

    /** Grey: black at #1F1E1D, the lighter tiers above it. Matches the Warm graphite patch. */
    static int grey(int level) {
        return warm(31 + Math.round(level * 0.55f));
    }

    /** Black: TikTok's page grays at pure black, its cards and lines just above it. */
    static int blackTier(int level) {
        return level <= 0x14 ? 0 : warm(Math.round((level - 0x14) * 0.7f));
    }

    private static int warm(int level) {
        int green = level - Math.round(level * 0.03f);
        int blue = level - Math.round(level * 0.07f);
        return (level << 16) | (green << 8) | blue;
    }

    /**
     * Saves the dark theme and repaints every open screen with it. Screens take their colours as
     * they are built, so each one is rebuilt, as TikTok does when its own theme changes.
     */
    public static boolean save(Context context, String style) {
        try {
            if (style.equals(Settings.NINETOK_DARK_STYLE.get())) return false;
            Settings.NINETOK_DARK_STYLE.save(style);
            Context application = context.getApplicationContext();
            if (OLED.equals(style)) apply(application);
            else release(application);
            List<Activity> open;
            synchronized (LIVE) {
                open = new ArrayList<>(LIVE);
            }
            for (Activity activity : open) {
                if (OLED.equals(style)) apply(activity);
                else release(activity);
            }
            return true;
        } catch (Throwable error) {
            Logger.printException(() -> "Could not switch the dark theme", error);
            return false;
        }
    }

    /**
     * Starts TikTok over on its feed, in a fresh process. Every screen and everything TikTok
     * built once per run (its Compose palette among them) comes up in the theme just chosen. A
     * theme change that rebuilt screens one by one could leave one half in the old theme and not
     * answering until it was closed, and the Compose cards in the theme TikTok started in.
     */
    public static void restartToFeed(Context context) {
        try {
            Context application = context.getApplicationContext();
            Intent launch = application.getPackageManager().getLaunchIntentForPackage(application.getPackageName());
            if (launch == null) return;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            application.startActivity(launch);
            // Android starts the feed in a new process once this one is gone.
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        } catch (Throwable error) {
            Logger.printException(() -> "Could not start TikTok over on its feed", error);
        }
    }

    /**
     * {@link #restartToFeed} after TikTok has saved its own light or dark choice, a second later,
     * whatever TikTok did with its screens in the meantime.
     */
    public static void restartToFeedLater(Context context) {
        Context application = context.getApplicationContext();
        MAIN.postDelayed(() -> restartToFeed(application), 1200);
    }

    /** From the application's start and each activity: report a round screen while OLED is chosen. */
    public static void apply(Context context) {
        try {
            if (context == null || !oled()) return;
            setRound(context.getResources(), true);
        } catch (Throwable error) {
            Logger.printException(() -> "Could not apply the OLED theme", error);
        }
    }

    /** Back to the phone's own configuration when the choice leaves OLED, so the next screen is gray. */
    public static void release(Context context) {
        try {
            if (context != null) setRound(context.getResources(), false);
        } catch (Throwable error) {
            Logger.printException(() -> "Could not release the OLED theme", error);
        }
    }

    private static void setRound(Resources resources, boolean round) {
        Configuration configuration = new Configuration(resources.getConfiguration());
        int want = round ? Configuration.SCREENLAYOUT_ROUND_YES : Configuration.SCREENLAYOUT_ROUND_NO;
        if ((configuration.screenLayout & Configuration.SCREENLAYOUT_ROUND_MASK) == want) return;
        configuration.screenLayout = (configuration.screenLayout & ~Configuration.SCREENLAYOUT_ROUND_MASK) | want;
        resources.updateConfiguration(configuration, resources.getDisplayMetrics());
    }

    /**
     * From the main activity's onCreate: every later activity gets the same treatment before it
     * inflates, and a configuration change (which hands Resources the phone's own configuration
     * back) is followed by the choice again.
     */
    public static void install(Activity activity) {
        try {
            migrate();
            apply(activity);
            Application application = activity.getApplication();
            if (application == null || application == watching) return;
            watching = application;
            apply(application);
            application.registerComponentCallbacks(new ComponentCallbacks() {
                @Override public void onConfigurationChanged(Configuration configuration) {
                    apply(application);
                    List<Activity> open;
                    synchronized (LIVE) {
                        open = new ArrayList<>(LIVE);
                    }
                    for (Activity live : open) apply(live);
                }

                @Override public void onLowMemory() { }
            });
            application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityPreCreated(Activity created, Bundle state) {
                    apply(created);
                }

                @Override public void onActivityCreated(Activity created, Bundle state) {
                    synchronized (LIVE) {
                        LIVE.add(created);
                    }
                    apply(created);
                    TikTokTheme.remember(created);
                }

                @Override public void onActivityDestroyed(Activity destroyed) {
                    synchronized (LIVE) {
                        LIVE.remove(destroyed);
                    }
                }

                @Override public void onActivityStarted(Activity started) { }
                @Override public void onActivityResumed(Activity resumed) { }
                @Override public void onActivityPaused(Activity paused) { }
                @Override public void onActivityStopped(Activity stopped) { }
                @Override public void onActivitySaveInstanceState(Activity saved, Bundle state) { }
            });
            synchronized (LIVE) {
                LIVE.add(activity);
            }
        } catch (Throwable error) {
            Logger.printException(() -> "Could not watch activities for the theme", error);
        }
    }

    /**
     * The settings' old theme menu is gone: light or dark now follows TikTok. A saved "OLED
     * black" carries over as the dark theme, and the old choice goes back to following TikTok.
     */
    private static void migrate() {
        try {
            String old = Settings.NINETOK_THEME.get();
            if ("tiktok".equals(old)) return;
            if (OLED.equals(old)) Settings.NINETOK_DARK_STYLE.save(OLED);
            Settings.NINETOK_THEME.save("tiktok");
        } catch (Throwable error) {
            Logger.printException(() -> "Could not carry over the old theme choice", error);
        }
    }
}
