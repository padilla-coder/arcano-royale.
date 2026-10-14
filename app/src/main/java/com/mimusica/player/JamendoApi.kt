package com.mimusica.player

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// >>> PEGA AQUÍ TU CLIENT_ID DE JAMENDO (entre las comillas) <<<
const val JAMENDO_CLIENT_ID = "PON_AQUI_TU_CLIENT_ID"

data class OnlineTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int,
    val imageUrl: String,
    val streamUrl: String,
    val downloadUrl: String,
    val downloadAllowed: Boolean
)

fun OnlineTrack.toSong(): Song = Song(
    id = -(id.toLongOrNull() ?: 0L),
    title = title,
    artist = artist,
    album = album,
    albumId = 0L,
    durationMs = durationSec * 1000L,
    dateAdded = 0L,
    folder = "",
    sizeBytes = 0L,
    format = "Online",
    remoteArt = imageUrl
)

fun OnlineTrack.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId("j:$id")
    .setUri(streamUrl)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(Uri.parse(imageUrl))
            .build()
    )
    .build()

object JamendoApi {
    suspend fun tracks(query: String?, tag: String?, order: String, limit: Int = 30): List<OnlineTrack> =
        withContext(Dispatchers.IO) {
            val url = StringBuilder("https://api.jamendo.com/v3.0/tracks/")
                .append("?client_id=").append(JAMENDO_CLIENT_ID)
                .append("&format=json&limit=").append(limit)
                .append("&imagesize=300&order=").append(order)
            if (!query.isNullOrBlank()) url.append("&search=").append(URLEncoder.encode(query, "UTF-8"))
            if (!tag.isNullOrBlank()) url.append("&tags=").append(URLEncoder.encode(tag, "UTF-8"))

            val conn = URL(url.toString()).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            try {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONObject(body).getJSONArray("results")
                val out = mutableListOf<OnlineTrack>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val dl = o.optString("audiodownload")
                    out += OnlineTrack(
                        id = o.optString("id"),
                        title = o.optString("name"),
                        artist = o.optString("artist_name"),
                        album = o.optString("album_name"),
                        durationSec = o.optInt("duration"),
                        imageUrl = o.optString("image"),
                        streamUrl = o.optString("audio"),
                        downloadUrl = dl,
                        downloadAllowed = o.optBoolean("audiodownload_allowed", dl.isNotEmpty()) && dl.isNotEmpty()
                    )
                }
                out
            } finally {
                conn.disconnect()
            }
        }
}

object Downloader {
    /** Descarga la canción a Música/Jamendo para que aparezca en tu biblioteca y suene sin internet. */
    suspend fun download(context: Context, t: OnlineTrack, onProgress: (Float) -> Unit): Long? =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            var newUri: Uri? = null
            try {
                val name = "${t.artist} - ${t.title}".replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".mp3"
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.Audio.Media.TITLE, t.title)
                    put(MediaStore.Audio.Media.ARTIST, t.artist)
                    put(MediaStore.Audio.Media.ALBUM, t.album)
                    put(MediaStore.Audio.Media.IS_MUSIC, 1)
                    if (Build.VERSION.SDK_INT >= 29) {
                        put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Jamendo")
                        put(MediaStore.Audio.Media.IS_PENDING, 1)
                    }
                }
                newUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                val target = newUri ?: return@withContext null

                val conn = URL(t.downloadUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    resolver.openOutputStream(target)!!.use { out ->
                        val buf = ByteArray(32 * 1024)
                        var read: Int
                        var sum = 0L
                        var lastPct = -1
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            sum += read
                            if (total > 0) {
                                val pct = (sum * 100 / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress(pct / 100f)
                                }
                            }
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    val done = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
                    resolver.update(target, done, null, null)
                }
                ContentUris.parseId(target)
            } catch (e: Exception) {
                newUri?.let { try { resolver.delete(it, null, null) } catch (_: Exception) {} }
                null
            }
        }
}

fun OnlineTrack.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", title)
    put("artist_name", artist)
    put("album_name", album)
    put("duration", durationSec)
    put("image", imageUrl)
    put("audio", streamUrl)
    put("audiodownload", downloadUrl)
    put("audiodownload_allowed", downloadAllowed)
}

fun trackFromJson(o: JSONObject): OnlineTrack {
    val dl = o.optString("audiodownload")
    return OnlineTrack(
        id = o.optString("id"),
        title = o.optString("name"),
        artist = o.optString("artist_name"),
        album = o.optString("album_name"),
        durationSec = o.optInt("duration"),
        imageUrl = o.optString("image"),
        streamUrl = o.optString("audio"),
        downloadUrl = dl,
        downloadAllowed = o.optBoolean("audiodownload_allowed", dl.isNotEmpty()) && dl.isNotEmpty()
    )
}
