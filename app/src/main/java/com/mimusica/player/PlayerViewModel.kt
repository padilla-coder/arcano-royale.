package com.mimusica.player

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("mimusica", Context.MODE_PRIVATE)

    var allSongs by mutableStateOf<List<Song>>(emptyList()); private set
    var hiddenIds by mutableStateOf(prefs.getStringSet("hidden_ids", emptySet())!!.toSet()); private set
    var hiddenFolders by mutableStateOf(prefs.getStringSet("hidden_folders", emptySet())!!.toSet()); private set
    var favorites by mutableStateOf(
        prefs.getStringSet("favorites", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
    ); private set
    var plays by mutableStateOf<Map<Long, Int>>(emptyMap()); private set

    var minDurationSec by mutableIntStateOf(prefs.getInt("min_dur", 0)); private set
    var minSizeKb by mutableIntStateOf(prefs.getInt("min_size_kb", 0)); private set
    var disabledFormats by mutableStateOf(prefs.getStringSet("disabled_formats", emptySet())!!.toSet()); private set

    val songs: List<Song> by derivedStateOf {
        allSongs.filter {
            it.id.toString() !in hiddenIds && it.folder !in hiddenFolders &&
                it.durationMs >= minDurationSec * 1000L &&
                it.sizeBytes >= minSizeKb * 1024L &&
                it.format !in disabledFormats
        }
    }
    val hiddenSongs: List<Song> by derivedStateOf {
        allSongs.filter { it.id.toString() in hiddenIds }
    }

    var autoContinue by mutableStateOf(prefs.getBoolean("auto_continue", true)); private set

    // ---- Explorar (Jamendo) ----
    var onlineTracks by mutableStateOf<List<OnlineTrack>>(emptyList()); private set
    var onlineLoading by mutableStateOf(false); private set
    var onlineError by mutableStateOf<String?>(null); private set
    val downloadProgress = mutableStateMapOf<String, Float>()
    var downloadedOnline by mutableStateOf(
        prefs.getStringSet("jamendo_downloaded", emptySet())!!.mapNotNull { s ->
            val p = s.split(":")
            if (p.size == 2) p[0] to (p[1].toLongOrNull() ?: 0L) else null
        }.toMap()
    ); private set
    private val onlineSongs = mutableMapOf<String, Song>()

    var savedOnline by mutableStateOf(loadSaved()); private set

    private fun loadSaved(): List<OnlineTrack> = try {
        val arr = org.json.JSONArray(prefs.getString("saved_online", "[]"))
        (0 until arr.length()).map { trackFromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    fun isSaved(t: OnlineTrack): Boolean = savedOnline.any { it.id == t.id }

    fun toggleSaved(t: OnlineTrack) {
        savedOnline = if (isSaved(t)) savedOnline.filter { it.id != t.id } else listOf(t) + savedOnline
        val arr = org.json.JSONArray()
        savedOnline.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("saved_online", arr.toString()).apply()
    }

    private fun saveDownloaded() {
        putSet("jamendo_downloaded", downloadedOnline.map { "${it.key}:${it.value}" }.toSet())
    }

    fun localSongFor(t: OnlineTrack): Song? =
        downloadedOnline[t.id]?.let { lid -> allSongs.firstOrNull { it.id == lid } }

    fun isDownloaded(t: OnlineTrack): Boolean {
        val lid = downloadedOnline[t.id] ?: return false
        return allSongs.any { it.id == lid }
    }

    var current by mutableStateOf<Song?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var positionMs by mutableLongStateOf(0L); private set
    var durationMs by mutableLongStateOf(0L); private set
    var shuffle by mutableStateOf(false); private set

    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@PlayerViewModel.isPlaying = isPlaying
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.mediaId?.toLongOrNull()?.let { countPlay(it) }
            syncCurrent()
        }
        override fun onPlaybackStateChanged(playbackState: Int) = syncCurrent()
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            controller = future.get().also { it.addListener(listener) }
            syncCurrent()
        }, ContextCompat.getMainExecutor(app))

        viewModelScope.launch {
            while (true) {
                controller?.let { if (it.isPlaying) positionMs = it.currentPosition }
                delay(500)
            }
        }
    }

    private fun putSet(key: String, value: Set<String>) {
        prefs.edit().putStringSet(key, value).apply()
    }

    private fun countPlay(id: Long) {
        val n = (plays[id] ?: 0) + 1
        plays = plays + (id to n)
        prefs.edit().putInt("plays_$id", n).apply()
    }

    private fun syncCurrent() {
        val c = controller ?: return
        val mid = c.currentMediaItem?.mediaId
        val id = mid?.toLongOrNull()
        current = id?.let { i -> allSongs.firstOrNull { it.id == i } } ?: mid?.let { onlineSongs[it] }
        durationMs = if (c.duration == C.TIME_UNSET) 0L else c.duration
        positionMs = c.currentPosition
        isPlaying = c.isPlaying
        shuffle = c.shuffleModeEnabled
    }

    fun loadSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = MusicRepository.loadSongs(getApplication())
            val counts = list.mapNotNull { s ->
                prefs.getInt("plays_${s.id}", 0).takeIf { it > 0 }?.let { s.id to it }
            }.toMap()
            withContext(Dispatchers.Main) {
                allSongs = list
                plays = counts
                syncCurrent()
            }
        }
    }

    // ---- Filtros ----
    fun setMinDuration(sec: Int) {
        minDurationSec = sec
        prefs.edit().putInt("min_dur", sec).apply()
    }

    fun setMinSize(kb: Int) {
        minSizeKb = kb
        prefs.edit().putInt("min_size_kb", kb).apply()
    }

    fun toggleFormat(f: String) {
        disabledFormats = if (f in disabledFormats) disabledFormats - f else disabledFormats + f
        putSet("disabled_formats", disabledFormats)
    }

    fun resetFilters() {
        setMinDuration(0)
        setMinSize(0)
        disabledFormats = emptySet()
        putSet("disabled_formats", disabledFormats)
    }

    // ---- Ocultar / favoritos ----
    fun hideSong(s: Song) {
        hiddenIds = hiddenIds + s.id.toString()
        putSet("hidden_ids", hiddenIds)
    }

    fun unhideSong(s: Song) {
        hiddenIds = hiddenIds - s.id.toString()
        putSet("hidden_ids", hiddenIds)
    }

    fun hideFolder(folder: String) {
        hiddenFolders = hiddenFolders + folder
        putSet("hidden_folders", hiddenFolders)
    }

    fun unhideFolder(folder: String) {
        hiddenFolders = hiddenFolders - folder
        putSet("hidden_folders", hiddenFolders)
    }

    fun toggleFavorite(s: Song) {
        favorites = if (s.id in favorites) favorites - s.id else favorites + s.id
        putSet("favorites", favorites.map { it.toString() }.toSet())
    }

    // ---- Reproducción ----
    fun play(list: List<Song>, index: Int) {
        val c = controller ?: return
        c.shuffleModeEnabled = false
        // Al terminar la lista, sigue con otras canciones de tu biblioteca
        val queue = if (autoContinue) {
            val ids = list.map { it.id }.toSet()
            list + songs.filter { it.id !in ids }.shuffled()
        } else list
        c.setMediaItems(queue.map { it.toMediaItem() }, index, 0L)
        c.prepare()
        c.play()
        syncCurrent()
    }

    fun setAutoContinue(v: Boolean) {
        autoContinue = v
        prefs.edit().putBoolean("auto_continue", v).apply()
    }

    fun searchOnline(query: String?, tag: String?) {
        viewModelScope.launch {
            onlineLoading = true
            onlineError = null
            try {
                val order = if (query.isNullOrBlank()) "popularity_week" else "relevance"
                onlineTracks = JamendoApi.tracks(query, tag, order)
            } catch (e: Exception) {
                onlineError = "No se pudo conectar. Revisa tu internet y tu client_id de Jamendo."
            }
            onlineLoading = false
        }
    }

    fun playOnline(list: List<OnlineTrack>, index: Int) {
        val c = controller ?: return
        list.forEach { onlineSongs["j:${it.id}"] = it.toSong() }
        c.shuffleModeEnabled = false
        // Si ya la descargaste, suena la copia local (sin internet)
        c.setMediaItems(list.map { t -> localSongFor(t)?.toMediaItem() ?: t.toMediaItem() }, index, 0L)
        c.prepare()
        c.play()
        syncCurrent()
    }

    fun downloadOnline(t: OnlineTrack) {
        val ctx = getApplication<Application>()
        if (!t.downloadAllowed || t.downloadUrl.isEmpty()) {
            Toast.makeText(ctx, "El artista no permite descargar esta canción", Toast.LENGTH_SHORT).show()
            return
        }
        if (isDownloaded(t) || downloadProgress.containsKey(t.id)) return
        downloadProgress[t.id] = 0f
        viewModelScope.launch {
            val localId = Downloader.download(ctx, t) { p -> downloadProgress[t.id] = p }
            downloadProgress.remove(t.id)
            if (localId != null) {
                downloadedOnline = downloadedOnline + (t.id to localId)
                saveDownloaded()
                loadSongs()
                Toast.makeText(ctx, "Descargada: ${t.title}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, "No se pudo descargar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shufflePlay(list: List<Song>) {
        if (list.isEmpty()) return
        val c = controller ?: return
        c.setMediaItems(list.map { it.toMediaItem() }, Random.nextInt(list.size), 0L)
        c.shuffleModeEnabled = true
        c.prepare()
        c.play()
        syncCurrent()
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            play(listOf(song), 0)
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
        }
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
        shuffle = c.shuffleModeEnabled
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        positionMs = ms
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        super.onCleared()
    }
}
