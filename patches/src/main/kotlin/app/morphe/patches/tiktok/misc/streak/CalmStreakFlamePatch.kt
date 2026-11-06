/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.misc.streak

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
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val EXTENSION = "Lapp/morphe/extension/tiktok/inbox/StreakFlame;"

/**
 * The streak badge's update, a static method taking the badge first. When the streak lights up
 * (grey to orange) or the badge appears lit, and the DM streak experiment's "enableUpdateAnim"
 * is on, it rolls the number and plays a Lottie flame in place of the icon.
 */
private object StreakBadgeUpdateFingerprint : Fingerprint(
    returnType = "Z",
    strings = listOf(string("StreakBadgeView: updateStyle, style=")),
)

@Suppress("unused")
val calmStreakFlamePatch = bytecodePatch(
    name = "Calm streak flame",
    description = "When a chat streak goes up, the flame by the name fades in softly instead of " +
        "TikTok's flame burst and rolling number. Switch in NINE’tok settings > Interface > Motion.",
) {
    category("Interaction")
    dependsOn(settingsPatch, sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok())

    execute {
        StreakBadgeUpdateFingerprint.method.apply {
            val flagIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.IGET_BOOLEAN &&
                    getReference<FieldReference>()?.name == "enableUpdateAnim"
            }
            val flag = getInstruction<TwoRegisterInstruction>(flagIndex).registerA
            // Right after reading it TikTok branches: off goes to the plain update; then whether
            // the streak lit up, and at that test's target, whether the badge appeared lit.
            val offIndex = indexOfFirstInstructionOrThrow(flagIndex + 1, Opcode.IF_EQZ)
            if (getInstruction<OneRegisterInstruction>(offIndex).registerA != flag) {
                throw PatchException("Calm streak flame: the badge update no longer tests its flag first")
            }
            val litIndex = indexOfFirstInstructionOrThrow(offIndex + 1, Opcode.IF_EQZ)
            val lit = getInstruction<OneRegisterInstruction>(litIndex).registerA
            val appearedIndex = getInstruction<BuilderOffsetInstruction>(litIndex).target.location.index
            if (getInstruction(appearedIndex).opcode != Opcode.IF_EQZ) {
                throw PatchException("Calm streak flame: the badge update's second test moved")
            }
            val appeared = getInstruction<OneRegisterInstruction>(appearedIndex).registerA
            // Static, so the badge (the first parameter) is the first parameter register.
            val parameterWords = parameterTypes.fold(0) { words, type ->
                val name = type.toString()
                words + if (name == "J" || name == "D") 2 else 1
            }
            val badge = implementation!!.registerCount - parameterWords
            if (listOf(badge, flag, lit, appeared).any { it > 15 }) {
                throw PatchException("Calm streak flame: the badge update's registers are out of reach")
            }
            addInstructions(
                flagIndex + 1,
                """
                    invoke-static {v$badge, v$flag, v$lit, v$appeared}, $EXTENSION->updateAnim(Landroid/view/View;ZZZ)Z
                    move-result v$flag
                """,
            )
        }

        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableCalmStreakFlame()V",
        )
    }
}
