/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.misc.groupchat

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.patches.tiktok.shared.requireLocals
import app.morphe.util.addInstruction
import app.morphe.util.addInstructionsWithLabels
import com.android.tools.smali.dexlib2.AccessFlags

private const val EXTENSION = "Lapp/morphe/extension/tiktok/misc/GroupChats;"

/**
 * TikTok's answer to "may this account make group chats": a static, argument-free method that
 * reads the "group_chat" item of the IM privacy restriction and returns TRUE, FALSE or null.
 * Every group entry point (Inbox's create button, the share panel, chat details) asks it.
 */
private object GroupChatAllowedFingerprint : Fingerprint(
    returnType = "Ljava/lang/Boolean;",
    parameters = emptyList(),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    strings = listOf(string("group_chat")),
)

@Suppress("unused")
val allowGroupChatsPatch = bytecodePatch(
    name = "Allow group chats",
    description = "Offers group chats, including making a new group, when TikTok's restriction " +
        "for your account hides them. TikTok's servers can still refuse a group they do not " +
        "allow, and they do for some accounts. Starts off. Turn it on in NINE’tok settings > Interface.",
) {
    category("Interaction")
    dependsOn(settingsPatch, sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok())

    execute {
        GroupChatAllowedFingerprint.method.apply {
            requireLocals("Allow group chats", 1)
            addInstructionsWithLabels(
                0,
                """
                    invoke-static {}, $EXTENSION->forced()Ljava/lang/Boolean;
                    move-result-object v0
                    if-eqz v0, :tiktok_decides
                    return-object v0
                    :tiktok_decides
                    nop
                """,
            )
        }

        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableGroupChats()V",
        )
    }
}
