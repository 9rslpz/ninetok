/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.feed;

import app.morphe.extension.tiktok.settings.Settings;

/**
 * TikTok's AI object search on videos: the "Find hats" and "Find electronics" tags over things
 * it spots in a video, and the bubble offering them. Each is switched on by an experiment value;
 * while "Hide AI object search" is on, TikTok reads 0 there, which is off.
 */
public final class VisualSearch {
    private VisualSearch() {
    }

    /** Called with the experiment value TikTok read; returns the one it goes on with. */
    public static int flag(int value) {
        try {
            return Settings.HIDE_VISUAL_SEARCH.get() ? 0 : value;
        } catch (Throwable error) {
            return value;
        }
    }
}
