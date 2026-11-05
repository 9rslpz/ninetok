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
        val expected = HexFormat.of().parseHex("6ed491132d4f2066cfbe585227243b2877f27e195648718a9e46053555606687")
        for (name in listOf("patches-bundle.png", "assets/readme-header.png")) {
            assertArrayEquals(name, expected, MessageDigest.getInstance("SHA-256").digest(File(root, name).readBytes()))
        }
    }

    @Test fun `the README credits the upstream patches and their author`() {
        val readme = File(root, "README.md").readText()
        assertTrue(readme.contains("https://github.com/icysymmetra/tiktok-patches-for-morphe"))
        assertTrue(readme.contains("[NOTICE](NOTICE)"))
        val index = JsonParser.parseString(File(root, "patches-bundle.json").readText()).asJsonObject
        assertTrue(index.get("download_url").asString.startsWith("https://github.com/"))
    }
}
