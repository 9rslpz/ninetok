/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.misc;

import app.morphe.extension.tiktok.settings.Settings;

/**
 * Group chats. TikTok decides whether to offer them from a privacy restriction the server sends
 * for the account ("group_chat"), and hides creating one when it says no. This answers yes in
 * its place while the switch is on. The server can still refuse a group it does not allow.
 */
public final class GroupChats {
    private GroupChats() {
    }

    /** TRUE to offer group chats whatever the restriction says, or null to let TikTok decide. */
    public static Boolean forced() {
        return Settings.ALLOW_GROUP_CHATS.get() ? Boolean.TRUE : null;
    }
}
