/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.misc.groupchat

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.util.addInstructions
import com.android.tools.smali.dexlib2.AccessFlags

private const val LOG = "Lapp/morphe/extension/tiktok/misc/GroupCreateLog;"

/**
 * The two places TikTok handles a failed group creation: each takes the error object (status,
 * code, check code and the server's message) as its first parameter and logs "Group creation
 * failed" or "create group chat result: " itself, without any of those fields.
 */
private object GroupCreationFailedFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf(string("Group creation failed")),
    custom = { method, _ -> method.parameterTypes.size == 2 && method.name == "LJ" },
)

private object GroupChatResultFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf(string("create group chat result: ")),
    custom = { method, _ -> method.parameterTypes.size == 2 && method.name == "LJ" },
)

@Suppress("unused")
val groupCreateLogPatch = bytecodePatch(
    name = "Group creation log",
    description = "Writes what TikTok's servers answered when making a group chat fails (status, " +
        "code, check code and message) to logcat under \"ninetok-group\". For finding out why " +
        "groups are refused. Not for builds you hand to others.",
    default = false,
) {
    category("Diagnostics")
    dependsOn(sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok())

    execute {
        for ((fingerprint, method) in listOf(
            GroupCreationFailedFingerprint to "failedHandler",
            GroupChatResultFingerprint to "resultHandler",
        )) {
            fingerprint.method.apply {
                // The error object is the first parameter: p1 on an instance method, p0 on a static one.
                val error = if (AccessFlags.STATIC.isSet(accessFlags)) "p0" else "p1"
                addInstructions(
                    0,
                    "invoke-static/range {$error .. $error}, $LOG->$method(Ljava/lang/Object;)V",
                )
            }
        }
    }
}
