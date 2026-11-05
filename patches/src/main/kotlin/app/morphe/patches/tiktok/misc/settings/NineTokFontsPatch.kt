package app.morphe.patches.tiktok.misc.settings

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch

/** Where the settings screen's fonts land in the APK; NineTokType in the extension reads them. */
internal const val NINETOK_FONT_ASSETS = "assets/ninetok/fonts"

/**
 * The settings screen's typefaces: Manrope (SIL Open Font License, see OFL-Manrope.txt) for the
 * text, and the Yaroslav Hand 14 handwriting for the letters NINE in the name.
 */
internal val NINETOK_FONTS = listOf("sans-regular.ttf", "sans-medium.ttf", "sans-semibold.ttf", "hand.ttf", "OFL-Manrope.txt")

/** A class of this bundle, so the lookup goes through the class loader that holds the fonts. */
private object NineTokFontResources

/** Copies the bundled fonts into the APK's assets, which every Android version can read. */
internal val nineTokFontsPatch = rawResourcePatch {
    execute {
        for (name in NINETOK_FONTS) {
            val font = NineTokFontResources::class.java.getResourceAsStream("/ninetok/fonts/$name")
                ?: throw PatchException("NINE’tok font $name is missing from the patch bundle")
            val target = get("$NINETOK_FONT_ASSETS/$name")
            target.parentFile.mkdirs()
            font.use { input -> target.outputStream().use { input.copyTo(it) } }
        }
    }
}
