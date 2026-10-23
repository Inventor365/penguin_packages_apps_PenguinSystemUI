/*
 * Copyright (C) 2026 The PenguinOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.keyguard.ui.composable.media

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONTokener
import org.json.JSONException

internal object MotionArt {
    enum class Kind(val key: String, val targetWidth: Int, val suffix: String) {
        SQUARE("motionDetailSquare", 768, ""),
        TALL("motionDetailTall", 830, "-tall"),
    }

    // Tablets fetch the iPad renditions (up to 2048 wide, HEVC only) instead of the phone ones.
    private const val TABLET_MIN_WIDTH_DP = 600
    private const val TABLET_TARGET_WIDTH = 2048
    private const val TABLET_SUFFIX = "-tablet"

    private const val TAG = "LockscreenMotionArt"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/138.0 Mobile Safari/537.36"
    private const val MAX_BYTES = 24L * 1024 * 1024
    private const val MAX_CACHED = 40

    private val albums = ConcurrentHashMap<String, String>()
    private val releaseAliases = ConcurrentHashMap<String, String>()
    private val lookupMutex = Mutex()
    private const val MAX_RELEASES = 6

    suspend fun find(context: Context, artist: String, title: String, kind: Kind): File? =
        withContext(Dispatchers.IO) {
            lookupMutex.withLock {
                if (normalise(artist).isBlank() || normalise(title).isBlank()) return@withLock null
                val dir = File(context.createDeviceProtectedStorageContext().cacheDir, "motion_art")
                val tablet = context.resources.configuration.smallestScreenWidthDp >= TABLET_MIN_WIDTH_DP
                val suffix = kind.suffix + if (tablet) TABLET_SUFFIX else ""
                val key = normalise(artist) + "\u0000" + normalise(title) + "\u0000" + kind
                fun cached(id: String): File? =
                    File(dir, id + suffix + ".mp4").takeIf { it.isFile && it.length() > 0 }
                        ?.also { it.setLastModified(System.currentTimeMillis()) }
                albums[key]?.let { id ->
                    cached(id)?.let { return@withLock it }
                    albums.remove(key)
                }
                try {
                    val queue = ArrayDeque(findAlbums(artist, title))
                    val visited = mutableSetOf<String>()
                    val parents = mutableMapOf<String, String>()
                    while (queue.isNotEmpty() && visited.size < MAX_RELEASES) {
                        currentCoroutineContext().ensureActive()
                        val (id, page) = queue.removeFirst()
                        if (!visited.add(id)) continue
                        val resolved = releaseAliases["$id\u0000$kind"] ?: id
                        cached(resolved)?.let {
                            albums[key] = resolved
                            return@withLock it
                        }
                        try {
                            val html = fetch(page)
                            currentCoroutineContext().ensureActive()
                            val target = File(dir, id + suffix + ".mp4")
                            if (download(html, kind, target, tablet)) {
                                albums[key] = id
                                var parent = parents[id]
                                while (parent != null) {
                                    releaseAliases["$parent\u0000$kind"] = id
                                    parent = parents[parent]
                                }
                                trim(dir)
                                Log.d(TAG, "Resolved $kind animation from release $id")
                                return@withLock target
                            }
                            alternateAlbums(html).forEach {
                                if (it.first !in visited && !parents.containsKey(it.first)) {
                                    parents[it.first] = id
                                    queue.addLast(it)
                                }
                            }
                        } catch (e: IOException) {
                            Log.w(TAG, "Release $id ($kind) lookup failed: " + e.message)
                        }
                    }
                    Log.d(TAG, "No $kind animation in " + visited.size + " matching releases")
                } catch (e: IOException) {
                    Log.w(TAG, "Motion art search failed: " + e.message)
                } catch (e: JSONException) {
                    Log.w(TAG, "Invalid motion art search response", e)
                }
                // Misses are retried; a transient failure must not poison this process's cache.
                null
            }
        }

    private fun findAlbums(artist: String, title: String): List<Pair<String, String>> {
        val country = Locale.getDefault().country.lowercase(Locale.ROOT).ifEmpty { "us" }
        val term = URLEncoder.encode("$artist $title", "UTF-8")
        val json = JSONObject(fetch(
            "https://itunes.apple.com/search?term=$term&entity=song&limit=25&country=$country"
        ))
        val results = json.optJSONArray("results") ?: return emptyList()
        val wantedArtist = normalise(artist)
        val wantedTitle = normalise(title)
        val found = linkedMapOf<String, String>()
        for (i in 0 until results.length()) {
            val song = results.optJSONObject(i) ?: continue
            if (normalise(song.optString("artistName")) != wantedArtist ||
                normalise(song.optString("trackName")) != wantedTitle) continue
            val id = song.optLong("collectionId").takeIf { it > 0 } ?: continue
            val page = song.optString("collectionViewUrl").substringBefore('?')
            if (isAlbumPage(page)) found[id.toString()] = page
        }
        return found.map { it.key to it.value }
    }

    private fun isAlbumPage(page: String): Boolean = try {
        val url = URL(page)
        url.protocol == "https" && url.host == "music.apple.com" && "/album/" in url.path
    } catch (_: IOException) { false }

    private fun alternateAlbums(html: String): List<Pair<String, String>> {
        val data = Regex("""<script\b[^>]*\bid=["']serialized-server-data["'][^>]*>([\s\S]*?)</script>""")
            .find(html)?.groupValues?.get(1) ?: return emptyList()
        val found = linkedMapOf<String, String>()
        fun visit(value: Any?) {
            when (value) {
                is JSONObject -> {
                    if (value.optString("id") == "other-versions - other-versions") {
                        val items = value.optJSONArray("items") ?: return
                        for (i in 0 until items.length()) {
                            val item = items.optJSONObject(i)?.optJSONObject("contentDescriptor") ?: continue
                            val id = item.optJSONObject("identifiers")?.optString("storeAdamID").orEmpty()
                            val page = item.optString("url")
                            if (id.toLongOrNull() != null && isAlbumPage(page)) found[id] = page
                        }
                    } else {
                        value.keys().forEach { visit(value.opt(it)) }
                    }
                }
                is JSONArray -> for (i in 0 until value.length()) visit(value.opt(i))
            }
        }
        try { visit(JSONTokener(data).nextValue()) } catch (e: JSONException) {
            Log.w(TAG, "Cannot read alternate album releases", e)
        }
        return found.map { it.key to it.value }.take(MAX_RELEASES)
    }

    private fun normalise(text: String) =
        text.lowercase(Locale.ROOT).replace(Regex("\\(.*?\\)|\\[.*?]"), "").replace(Regex("[^\\p{L}\\p{N}]"), "")

    private suspend fun download(html: String, kind: Kind, target: File, tablet: Boolean): Boolean {
        val start = html.indexOf("\"${kind.key}\"")
        if (start < 0) return false
        val video =
            Regex("\"video\":\"(https://[^\"]+?\\.m3u8)\"")
                .find(html.substring(start, minOf(html.length, start + 4000)))
                ?.groupValues
                ?.get(1) ?: return false

        val master = fetch(video).lines()
        var best: String? = null
        var bestDistance = Int.MAX_VALUE
        var bestBandwidth = Long.MAX_VALUE
        for (i in master.indices) {
            val line = master[i]
            if (!line.startsWith("#EXT-X-STREAM-INF")) continue
            val hevc = tablet && line.contains("hvc1")
            if (!line.contains("avc1") && !hevc) continue
            val width =
                Regex("RESOLUTION=(\\d+)x").find(line)?.groupValues?.get(1)?.toInt() ?: continue
            val uri = master.getOrNull(i + 1)?.trim().orEmpty()
            if (uri.isEmpty() || uri.startsWith("#")) continue
            if (tablet) {
                val height =
                    Regex("RESOLUTION=\\d+x(\\d+)").find(line)?.groupValues?.get(1)?.toInt()
                        ?: continue
                val mime = if (hevc) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC
                if (!canDecode(mime, width, height)) continue
            }
            val distance = abs(width - if (tablet) TABLET_TARGET_WIDTH else kind.targetWidth)
            // Several bitrates share a resolution; tablets take the smallest file of the closest size.
            val bandwidth =
                Regex("[,:]BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLong()
                    ?: Long.MAX_VALUE
            if (distance < bestDistance ||
                (tablet && distance == bestDistance && bandwidth < bestBandwidth)) {
                best = URL(URL(video), uri).toString()
                bestDistance = distance
                bestBandwidth = bandwidth
            }
        }
        best ?: return false

        val map =
            Regex("#EXT-X-MAP:URI=\"([^\"]+)\"").find(fetch(best))?.groupValues?.get(1)
                ?: return false
        target.parentFile?.mkdirs()
        val partial = File(target.path + ".part")
        val connection = open(URL(URL(best), map).toString())
        try {
            if (connection.contentLengthLong > MAX_BYTES) return false
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_BYTES) throw IOException("Motion art exceeds cache limit")
                        output.write(buffer, 0, count)
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            if (partial.length() !in 1..MAX_BYTES || !partial.renameTo(target)) return false
            return true
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }

    private fun canDecode(mime: String, width: Int, height: Int): Boolean =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder && info.isHardwareAccelerated &&
                info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                try {
                    info.getCapabilitiesForType(mime).videoCapabilities?.isSizeSupported(width, height) == true
                } catch (_: IllegalArgumentException) {
                    false
                }
        }

    private fun trim(dir: File) {
        val files = dir.listFiles { file -> file.name.endsWith(".mp4") } ?: return
        files.sortedByDescending { it.lastModified() }.drop(MAX_CACHED).forEach { it.delete() }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", USER_AGENT)
        }

    private fun fetch(url: String): String {
        val connection = open(url)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode} for $url")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
