/*
 * Animated emoji catalog integrity checks.
 *
 * The asset catalog is generated from a third-party mirror; these tests guard against
 * accidentally shipping an empty/truncated manifest or unsafe/malformed asset paths.
 */
package im.vector.app.features.animatedemoji

import androidx.test.platform.app.InstrumentationRegistry
import im.vector.app.R
import org.json.JSONArray
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramEmojiAnimationCatalogTest {

    @Test
    fun catalogContainsTheExpectedFeatureFamilies() {
        val rows = readCatalog()
        val types = rows.mapNotNull { it.optString("type").takeIf(String::isNotBlank) }.toSet()

        assertTrue("Animated emoji entries are missing", "animated" in types)
        assertTrue("Reaction animation entries are missing", "reactions" in types)
        assertTrue("Generic animation entries are missing", "generic_anim" in types)
        assertTrue("Dice animation entries are missing", "dice" in types)
    }

    @Test
    fun catalogHasHundredsOfEntriesAndNoUnsafePaths() {
        val rows = readCatalog()
        assertTrue("Catalog looks truncated: found only ${rows.size} rows", rows.size >= 500)

        rows.forEachIndexed { index, row ->
            val emoji = row.optString("emoji")
            val type = row.optString("type")
            val path = row.optString("file_lottie")
            assertFalse("Row $index has no emoji", emoji.isBlank())
            assertTrue("Row $index has unsupported type: $type", type in SUPPORTED_TYPES)
            assertTrue("Row $index has an invalid asset path: $path", path.startsWith("lottie/"))
            assertFalse("Row $index contains path traversal: $path", path.split('/').any { it == ".." })
        }
    }

    private fun readCatalog(): List<org.json.JSONObject> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val json = context.resources.openRawResource(R.raw.telegram_emoji_animation_catalog)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private companion object {
        val SUPPORTED_TYPES = setOf("animated", "reactions", "generic_anim", "dice")
    }
}
