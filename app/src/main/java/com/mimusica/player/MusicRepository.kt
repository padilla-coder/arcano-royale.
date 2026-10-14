package com.mimusica.player

import android.content.Context
import android.os.Build
import android.provider.MediaStore

object MusicRepository {
    private fun formatOf(name: String?): String =
        when (name?.substringAfterLast('.', "")?.lowercase()) {
            "mp3" -> "MP3"
            "flac" -> "FLAC"
            "m4a", "aac", "mp4", "3gp" -> "M4A / AAC"
            "ogg", "oga", "opus" -> "OGG / Opus"
            "wav" -> "WAV"
            else -> "Otros"
        }

    fun loadSongs(context: Context): List<Song> {
        val songs = mutableListOf<Song>()
        val cols = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DISPLAY_NAME
        )
        val hasPath = Build.VERSION.SDK_INT >= 29
        if (hasPath) cols += MediaStore.Audio.Media.RELATIVE_PATH

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val order = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols.toTypedArray(), selection, null, order
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val pathCol = if (hasPath) c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) else -1
            while (c.moveToNext()) {
                val artist = c.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Artista desconocido"
                songs += Song(
                    id = c.getLong(idCol),
                    title = c.getString(titleCol) ?: "Sin título",
                    artist = artist,
                    album = c.getString(albumCol) ?: "Álbum desconocido",
                    albumId = c.getLong(albumIdCol),
                    durationMs = c.getLong(durCol),
                    dateAdded = c.getLong(addedCol),
                    folder = if (pathCol >= 0) (c.getString(pathCol) ?: "") else "",
                    sizeBytes = c.getLong(sizeCol),
                    format = formatOf(c.getString(nameCol))
                )
            }
        }
        return songs
    }
}
