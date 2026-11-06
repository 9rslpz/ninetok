/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.interaction.repostpill

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.addInstruction
import app.morphe.util.addInstructions
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val PILL = "Lapp/morphe/extension/tiktok/repost/RepostPill;"
private const val AWEME = "Lcom/ss/android/ugc/aweme/feed/model/Aweme;"
private const val EXTRA = "Lcom/ss/android/ugc/aweme/upvote/event/UpvotePublishExtraData;"
private const val MOB = "Lcom/ss/android/ugc/aweme/upvote/event/UpvotePublishMobParam;"

private object AwemeSetUserDiggFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == AWEME && method.name == "setUserDigg" && method.returnType == "V" &&
            method.parameterTypes.map { it.toString() } == listOf("I")
    },
)

private object AwemeSetLikeFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == AWEME && method.name == "setLike" && method.returnType == "V" &&
            method.parameterTypes.map { it.toString() } == listOf("Z")
    },
)

/**
 * The share panel's Repost: given the video's publish data, its statistics and its id, it takes
 * the repost back when the user has one ("button") and publishes one otherwise ("share_panel").
 */
private object SharePanelRepostFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf(string("share_panel"), string("button")),
    custom = { method, _ ->
        val types = method.parameterTypes.map { it.toString() }
        types.size == 4 && types[0] == EXTRA && types[1] == MOB && types[2] == "Ljava/lang/String;"
    },
)

private fun String.javaName() = removePrefix("L").removeSuffix(";").replace('/', '.')

@Suppress("unused")
val repostPillPatch = bytecodePatch(
    name = "Repost button after a like",
    description = "Brings back the small Repost button next to the author's name that comes up " +
        "once you like a video. It reposts the way the share panel's Repost does; tap it again " +
        "to take the repost back. The like itself is TikTok's, untouched.",
) {
    category("Interaction")
    dependsOn(settingsPatch, sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok())

    execute {
        // Only told about the like, before TikTok stores it; the setters go on as they were.
        AwemeSetUserDiggFingerprint.method.addInstructions(
            0,
            "invoke-static/range {p0 .. p1}, $PILL->onUserDigg(Ljava/lang/Object;I)V",
        )
        AwemeSetLikeFingerprint.method.addInstructions(
            0,
            "invoke-static/range {p0 .. p1}, $PILL->onLike(Ljava/lang/Object;Z)V",
        )

        // The repost service the share panel calls: its holder field and the two methods.
        val share = SharePanelRepostFingerprint.method
        val references = share.implementation!!.instructions.mapNotNull { (it as? ReferenceInstruction)?.reference }
        val calls = references.filterIsInstance<MethodReference>()
        val publish = calls.firstOrNull { call ->
            val types = call.parameterTypes.map { it.toString() }
            types.size == 14 && EXTRA in types && MOB in types && types.last() == "Z"
        } ?: throw PatchException("Repost button: the share panel no longer publishes a repost here")
        val undo = calls.firstOrNull { call ->
            val types = call.parameterTypes.map { it.toString() }
            call.definingClass == publish.definingClass && types.size == 9 &&
                types[0] == "Ljava/lang/String;" && types[1] == MOB
        } ?: throw PatchException("Repost button: the share panel no longer takes a repost back here")
        val holder = references.filterIsInstance<FieldReference>().firstOrNull {
            it.definingClass == publish.definingClass && it.type == publish.definingClass
        } ?: throw PatchException("Repost button: the repost service's holder moved")

        // How the share panel tells whether you already reposted: the video's repost list from
        // the same service, read against the signed-in account's id.
        val list = calls.firstOrNull { call ->
            call.definingClass == publish.definingClass &&
                call.parameterTypes.map { it.toString() } == listOf("Ljava/lang/String;") &&
                call.returnType == "Lcom/ss/android/ugc/aweme/feed/model/upvote/UpvoteList;"
        } ?: throw PatchException("Repost button: the share panel no longer reads the repost list here")
        val account = calls.firstOrNull { call ->
            call.parameterTypes.isEmpty() && call.returnType == "Lcom/ss/android/ugc/aweme/IAccountUserService;"
        } ?: throw PatchException("Repost button: the share panel no longer asks for the account here")

        val targets = listOf(
            holder.definingClass.javaName(), holder.name, publish.name, undo.name,
            list.name, account.definingClass.javaName(), account.name,
        ).joinToString("|")
        val pill = mutableClassDefBy(PILL)
        val bridge = pill.methods.firstOrNull { it.name == "targets" && it.parameterTypes.isEmpty() }
            ?: throw PatchException("Repost button: the extension's targets bridge is missing")
        bridge.addInstructions(
            0,
            """
                const-string v0, "$targets"
                return-object v0
            """,
        )

        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableRepostAfterLike()V",
        )
    }
}
