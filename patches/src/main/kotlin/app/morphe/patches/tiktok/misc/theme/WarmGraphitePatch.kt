/*
 * NINE’tok, a modified version of icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.misc.theme

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import org.w3c.dom.Document
import org.w3c.dom.Element
import kotlin.math.roundToInt

@Suppress("unused")
val warmGraphitePatch = resourcePatch(
    name = "Warm graphite",
    description = "NINE’tok's Grey and Black dark themes over all of TikTok's dark mode. Grey: " +
        "every neutral dark of TikTok's dark themes, pure black included, moves onto a warm " +
        "graphite scale from #1F1E1D up, so the whole screen is gray with its tiers kept. Black " +
        "(chosen on TikTok's Display page): the same tiers drawn down to pure black. Needs " +
        "AMOLED dark theme set to #1F1E1D, which this builds on.",
    default = false,
) {
    category("Interface")
    compatibleWith(*AppCompatibilities.tiktok())
    // Runs after it: AMOLED dark theme writes the Black copies of TikTok's dark styles, and
    // this fills in their other dark tiers.
    dependsOn(amoledThemePatch)

    execute {
        val round = get("res").resolve("values-round/styles.xml")
        if (!round.exists()) throw PatchException("Warm graphite: AMOLED dark theme wrote no Black styles")

        // The dark styles: those AMOLED dark theme copied for Black, as they hold its surfaces.
        val black = mutableMapOf<String, MutableMap<String, String>>()
        var changed = 0
        val valuesDirectories = get("res").listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values") && it.name != "values-round" }
        document("res/values-round/styles.xml").use { roundStyles ->
            val darkStyles = styleNames(roundStyles)
            for (directory in valuesDirectories) {
                val file = directory.resolve("styles.xml")
                if (!file.exists()) continue
                document(file.relativeTo(get(".")).invariantSeparatorsPath).use { styles ->
                    val all = styles.getElementsByTagName("style")
                    for (index in 0 until all.length) {
                        val style = all.item(index) as Element
                        val name = style.getAttribute("name")
                        if (name !in darkStyles) continue
                        val items = style.getElementsByTagName("item")
                        for (i in 0 until items.length) {
                            val item = items.item(i) as Element
                            val level = neutralDark(item.textContent) ?: continue
                            item.textContent = grey(level)
                            black.getOrPut(name) { mutableMapOf() }[item.getAttribute("name")] = blackTier(level)
                            changed++
                        }
                    }
                }
            }
            // The Black copies: the same items, on the black scale.
            val copies = roundStyles.getElementsByTagName("style")
            for (index in 0 until copies.length) {
                val style = copies.item(index) as Element
                val tiers = black[style.getAttribute("name")] ?: continue
                val items = style.getElementsByTagName("item")
                for (i in 0 until items.length) {
                    val item = items.item(i) as Element
                    tiers[item.getAttribute("name")]?.let { item.textContent = it }
                }
            }
        }
        if (changed == 0) throw PatchException("Warm graphite: no dark grays found in TikTok's dark styles")
    }
}

private fun styleNames(styles: Document): Set<String> {
    val names = mutableSetOf<String>()
    val all = styles.getElementsByTagName("style")
    for (index in 0 until all.length) names += (all.item(index) as Element).getAttribute("name")
    return names
}

/**
 * The level of a neutral dark gray (`#vvvvvv` or `#FFvvvvvv` with v under 0x40), pure black
 * included, or null for anything else: colours, light grays and translucent overlays.
 */
internal fun neutralDark(value: String): Int? {
    val hex = value.trim().removePrefix("#")
    val rgb = when (hex.length) {
        6 -> hex
        8 -> if (hex.startsWith("ff", ignoreCase = true)) hex.substring(2) else return null
        else -> return null
    }
    if (!rgb.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    val (r, g, b) = rgb.chunked(2).map { it.toInt(16) }
    if (r != g || g != b || r >= 0x40) return null
    return r
}

/** Grey: black at #1F1E1D, the lighter tiers above it, a step apart as in TikTok's own scale. */
internal fun grey(level: Int): String = warm(31 + (level * 0.55).roundToInt())

/** Black: TikTok's page grays at pure black, its cards and lines just above it. */
internal fun blackTier(level: Int): String = if (level <= 0x14) "#FF000000" else warm(((level - 0x14) * 0.7).roundToInt())

/** NINE’tok's warmth: green a few percent down, blue some more, as in #1F1E1D, #262624, #30302E. */
internal fun warm(level: Int): String {
    val green = level - (level * 0.03).roundToInt()
    val blue = level - (level * 0.07).roundToInt()
    return "#FF%02X%02X%02X".format(level, green, blue)
}
