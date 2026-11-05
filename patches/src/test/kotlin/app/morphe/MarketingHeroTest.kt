package app.morphe

import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest
import java.util.HexFormat
import org.junit.Assert.*
import org.junit.Test

/** NINE’tok's artwork, and the credit its README owes the project it is built on. */
class MarketingHeroTest {
    private val root = File("..").canonicalFile.takeIf { File(it, "README.md").isFile }
        ?: File(".").canonicalFile

    @Test fun `the README and source icon show the NINE’tok artwork`() {
        val readme = File(root, "README.md").readText()
        assertTrue(readme.trimStart().startsWith("<p align=\"center\">"))
        assertTrue(readme.contains("assets/readme-header.png"))
        val expected = HexFormat.of().parseHex("292102929f6b65a94b9e1fbeba93545a0bced9faea3d41bf634eb916979d82d6")
        for (name in listOf("patches-bundle.png", "assets/readme-header.png")) {
            assertArrayEquals(name, expected, MessageDigest.getInstance("SHA-256").digest(File(root, name).readBytes()))
        }
    }

    @Test fun `the README credits the upstream patches and their author`() {
        val readme = File(root, "README.md").readText()
        assertTrue(readme.contains("https://github.com/icysymmetra/tiktok-patches-for-morphe"))
        assertTrue(readme.contains("https://ko-fi.com/P5P5YOUU7"))
        assertTrue(readme.contains("[NOTICE](NOTICE)"))
        val index = JsonParser.parseString(File(root, "patches-bundle.json").readText()).asJsonObject
        assertTrue(index.get("download_url").asString.startsWith("https://github.com/"))
    }
}
