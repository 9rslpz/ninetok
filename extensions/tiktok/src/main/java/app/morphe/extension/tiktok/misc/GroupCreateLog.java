/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.misc;

import android.util.Log;

import java.lang.reflect.Method;

/**
 * Writes what TikTok's servers answered when creating a group chat failed, to logcat under the
 * tag "ninetok-group". TikTok keeps that answer (a status, a code, a "check" code and the message
 * the server gave for it) in an error object and logs none of it. For tracking down why groups
 * are refused; nothing here is sent anywhere. Left out of builds for other people.
 */
public final class GroupCreateLog {
    private static final String TAG = "ninetok-group";
    private static final String[] GETTERS = {
            "getStatus", "getStatusMsg", "getCode", "getCheck", "getCheckMsg", "getExt", "getLogId", "getThrowable",
    };

    private GroupCreateLog() {
    }

    /** From TikTok's "Group creation failed" handler, with its error object. */
    public static void failedHandler(Object error) {
        error(error, "creation handler");
    }

    /** From TikTok's "create group chat result" handler, with its error object. */
    public static void resultHandler(Object error) {
        error(error, "result handler");
    }

    private static void error(Object error, String where) {
        try {
            StringBuilder out = new StringBuilder("create group failed in ").append(where).append(':');
            if (error == null) {
                out.append(" no error object");
            } else {
                for (String name : GETTERS) {
                    try {
                        Method method = error.getClass().getMethod(name);
                        Object value = method.invoke(error);
                        out.append(' ').append(name.substring(3)).append('=').append(value);
                    } catch (Throwable ignored) {
                        // A getter this build lacks is just left out.
                    }
                }
                out.append(" | ").append(error);
            }
            Log.w(TAG, out.toString());
        } catch (Throwable ignored) {
            // A log line must never break the group screen.
        }
    }
}
