/*
 * Animated-emoji catalog metadata is derived from the public Telemoji mirror.
 * See docs/TELEGRAM_ANIMATED_EMOJI_ASSETS.md for attribution and limitations.
 */
package im.vector.app.features.animatedemoji

import android.content.Context
import android.os.Handler
import android.os.Looper
import im.vector.app.R
import org.json.JSONArray
import timber.log.Timber
import java.util.concurrent.Executors

internal object TelegramEmojiAssetCatalog {
    private const val ASSET_BASE_URL = "https://raw.githubusercontent.com/saeedtahmtan/telemoji/main/"
    private val acceptedTypes = setOf("animated", "reactions", "generic_anim", "dice")
    private val loader = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "telegram-emoji-catalog").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var cachedIndex: Map<String, List<AnimationAsset>>? = null

    data class AnimationAsset(
            val emoji: String,
            val type: String,
            val path: String,
            val variant: Int
    ) {
        val url: String
            get() = TelegramEmojiAssetCatalog.assetUrl(path)
    }

    fun assetUrl(path: String): String = ASSET_BASE_URL + path

    /** Parse the compact manifest away from the UI thread. Callback always runs on main. */
    fun findAsync(
            context: Context,
            emoji: String,
            typesInPriorityOrder: List<String>,
            seed: Int = 0,
            callback: (AnimationAsset?) -> Unit
    ) {
        loader.execute {
            val asset = try {
                val index = index(context.applicationContext)
                val normalizedEmoji = normalizeEmoji(emoji)
                typesInPriorityOrder.asSequence()
                        .mapNotNull { type -> index[key(type, normalizedEmoji)] }
                        .firstOrNull { it.isNotEmpty() }
                        ?.let { variants -> variants[Math.floorMod(seed, variants.size)] }
            } catch (throwable: Throwable) {
                Timber.w(throwable, "Failed to look up animated emoji asset")
                null
            }
            mainHandler.post { callback(asset) }
        }
    }

    private fun index(context: Context): Map<String, List<AnimationAsset>> {
        cachedIndex?.let { return it }
        synchronized(this) {
            cachedIndex?.let { return it }
            val json = context.resources.openRawResource(R.raw.telegram_emoji_animation_catalog)
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
            val array = JSONArray(json)
            val mutable = HashMap<String, MutableList<AnimationAsset>>(array.length())
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                val emoji = row.optString("emoji").takeIf { it.isNotBlank() } ?: continue
                val type = row.optString("type").takeIf { it in acceptedTypes } ?: continue
                val path = row.optString("file_lottie").takeIf {
                    it.startsWith("lottie/") && !it.contains("..")
                } ?: continue
                val variant = row.optInt("variant", 1)
                mutable.getOrPut(key(type, normalizeEmoji(emoji))) { ArrayList() }
                        .add(AnimationAsset(emoji, type, path, variant))
            }
            return mutable.mapValues { (_, values) -> values.sortedBy { it.variant } }
                    .also { cachedIndex = it }
        }
    }

    private fun key(type: String, emoji: String): String = "$type\u0000$emoji"

    private fun normalizeEmoji(emoji: String): String = emoji
            .trim()
            .filterNot { it == '\uFE0E' || it == '\uFE0F' }
}
