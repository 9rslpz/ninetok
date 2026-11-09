/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.feedfilter

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.getReference
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.addInstruction
import app.morphe.util.addInstructions
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION = "Lapp/morphe/extension/tiktok/feed/VisualSearch;"

/**
 * The experiment values behind TikTok's AI object search on videos: the "Find hats", "Find
 * electronics" tags it lays over things it recognises in a video, and the bubble that offers them.
 * Each is read by a small lazy function that asks TikTok's experiment store for an int.
 */
private val FLAGS = listOf("visual_search_enable_feed_tag", "visual_search_fyp_bubble_switch")

private class VisualSearchFlagFingerprint(key: String) : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = emptyList(),
    strings = listOf(string(key)),
    custom = { method, _ -> method.name == "invoke" },
)

@Suppress("unused")
val hideVisualSearchPatch = bytecodePatch(
    name = "Hide AI object search",
    description = "Takes away the \"Find hats\", \"Find electronics\" and other tags TikTok's AI " +
        "puts on things it spots in a video, and the bubble offering them. Switch in NINE’tok " +
        "settings > Interface > Feed.",
) {
    category("Interface")
    dependsOn(settingsPatch, sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok())

    execute {
        for (key in FLAGS) {
            VisualSearchFlagFingerprint(key).method.apply {
                // The experiment read: an int getter that takes the key, and its result.
                val read = indexOfFirstInstructionOrThrow {
                    val reference = getReference<MethodReference>() ?: return@indexOfFirstInstructionOrThrow false
                    (opcode == Opcode.INVOKE_VIRTUAL || opcode == Opcode.INVOKE_INTERFACE) &&
                        reference.returnType == "I" &&
                        reference.parameterTypes.any { it.toString() == "Ljava/lang/String;" }
                }
                val move = read + 1
                if (getInstruction(move).opcode != Opcode.MOVE_RESULT) {
                    throw PatchException("Hide AI object search: $key is read in a new way")
                }
                val register = getInstruction<OneRegisterInstruction>(move).registerA
                addInstructions(
                    move + 1,
                    """
                        invoke-static {v$register}, $EXTENSION->flag(I)I
                        move-result v$register
                    """,
                )
            }
        }

        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableHideVisualSearch()V",
        )
    }
}
