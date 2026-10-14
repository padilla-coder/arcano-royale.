package com.mimusica.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val Bg = Color(0xFF030303)
private val Surface1 = Color(0xFF212121)
private val Accent = Color(0xFFFF0000)
private val Muted = Color(0xFFAAAAAA)

@Composable
fun MiMusicaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Bg, surface = Bg, primary = Accent, onBackground = Color.White, onSurface = Color.White
        ),
        content = {
            Surface(modifier = Modifier.fillMaxSize(), color = Bg, contentColor = Color.White) {
                content()
            }
        }
    )
}

sealed class Overlay {
    data class Detail(val title: String, val songs: List<Song>) : Overlay()
    object Search : Overlay()
    object Hidden : Overlay()
    object Settings : Overlay()
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

// ---------------------------------------------------------------- piezas reutilizables

@Composable
private fun Art(song: Song?, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).background(Surface1),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.MusicNote, null, tint = Muted)
        if (song != null) {
            AsyncImage(
                model = song.artUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun Pill(
    text: String, icon: ImageVector, filled: Boolean = true,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val bg = if (filled) Color.White else Color(0xFF3F3F3F)
    val fg = if (filled) Color.Black else Color.White
    Row(
        modifier.clip(RoundedCornerShape(50)).background(bg).clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = fg, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x4DFFFFFF)).clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) { Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White) }
}

@Composable
private fun SongMenuButton(song: Song, vm: PlayerViewModel, onDelete: (Song) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val fav = song.id in vm.favorites

    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, "Más opciones", tint = Muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Reproducir a continuación") },
                onClick = { open = false; vm.playNext(song) }
            )
            DropdownMenuItem(
                text = { Text(if (fav) "Quitar de Me gusta" else "Me gusta") },
                onClick = { open = false; vm.toggleFavorite(song) }
            )
            DropdownMenuItem(
                text = { Text("Ocultar esta canción") },
                onClick = { open = false; vm.hideSong(song) }
            )
            if (song.folder.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text("Ocultar carpeta ${song.folder}") },
                    onClick = { open = false; vm.hideFolder(song.folder) }
                )
            }
            DropdownMenuItem(
                text = { Text("Eliminar del teléfono", color = Color(0xFFFF6B6B)) },
                onClick = { open = false; confirm = true }
            )
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("¿Eliminar canción?") },
            text = { Text("Se borrará \"${song.title}\" de tu teléfono.") },
            confirmButton = {
                TextButton(onClick = { confirm = false; onDelete(song) }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun SongRow(
    song: Song, vm: PlayerViewModel, onDelete: (Song) -> Unit,
    big: Boolean = false, onClick: () -> Unit
) {
    val active = vm.current?.id == song.id
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }
            .padding(start = 16.dp, top = if (big) 7.dp else 4.dp, bottom = if (big) 7.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(song, Modifier.size(if (big) 64.dp else 48.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title, fontSize = if (big) 16.sp else 15.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = if (active) Accent else Color.White
            )
            Text(
                song.artist, color = Muted, fontSize = if (big) 14.sp else 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        SongMenuButton(song, vm, onDelete)
    }
}

@Composable
private fun SongList(
    songs: List<Song>, vm: PlayerViewModel, onDelete: (Song) -> Unit,
    modifier: Modifier = Modifier, onClick: (Int) -> Unit
) {
    LazyColumn(modifier) {
        items(songs.size, key = { songs[it].id }) { i ->
            SongRow(songs[i], vm, onDelete) { onClick(i) }
        }
    }
}

@Composable
private fun SongCard(song: Song, onClick: () -> Unit) {
    Column(Modifier.width(140.dp).clickable { onClick() }) {
        Art(song, Modifier.size(140.dp))
        Text(song.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(song.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AlbumCard(list: List<Song>, onClick: () -> Unit) {
    val first = list.first()
    Column(Modifier.width(140.dp).clickable { onClick() }) {
        Art(first, Modifier.size(140.dp))
        Text(first.album, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(first.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- app principal

@Composable
fun MusicApp(vm: PlayerViewModel, onDelete: (Song) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var showPlayer by remember { mutableStateOf(false) }

    BackHandler(enabled = showPlayer || overlay != null) {
        if (showPlayer) showPlayer = false else overlay = null
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val o = overlay) {
                    is Overlay.Detail -> DetailScreen(o, vm, onDelete) { overlay = null }
                    is Overlay.Search -> SearchScreen(vm, onDelete) { overlay = null }
                    is Overlay.Hidden -> HiddenScreen(vm) { overlay = null }
                    is Overlay.Settings -> SettingsScreen(vm, { overlay = it }) { overlay = null }
                    null -> when (tab) {
                        0 -> HomeScreen(vm, onDelete) { overlay = it }
                        1 -> SearchScreen(vm, onDelete, showBack = false) { tab = 0 }
                        else -> LibraryScreen(vm, onDelete) { overlay = it }
                    }
                }
            }
            if (vm.current != null) MiniPlayer(vm) { showPlayer = true }
            NavigationBar(containerColor = Bg) {
                val navItems = listOf(
                    Triple("Principal", Icons.Default.Home, 0),
                    Triple("Buscar", Icons.Default.Search, 1),
                    Triple("Biblioteca", Icons.Default.LibraryMusic, 2)
                )
                navItems.forEach { (label, icon, idx) ->
                    NavigationBarItem(
                        selected = tab == idx && overlay == null,
                        onClick = { tab = idx; overlay = null },
                        icon = { Icon(icon, null) },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }

        if (showPlayer && vm.current != null) FullPlayer(vm) { showPlayer = false }
    }
}

// ---------------------------------------------------------------- inicio

@Composable
private fun SectionHeader(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}

@Composable
private fun ShortcutCard(song: Song, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable { onClick() }) {
        Art(song, Modifier.fillMaxSize())
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .padding(start = 8.dp, end = 8.dp, top = 28.dp, bottom = 8.dp)
        ) {
            Text(
                song.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeScreen(vm: PlayerViewModel, onDelete: (Song) -> Unit, open: (Overlay) -> Unit) {
    val songs = vm.songs
    val recents = remember(songs) { songs.sortedByDescending { it.dateAdded } }
    val mostPlayed = remember(songs, vm.plays) {
        songs.filter { (vm.plays[it.id] ?: 0) > 0 }.sortedByDescending { vm.plays[it.id] ?: 0 }
    }
    val quick = remember(recents, mostPlayed) { (mostPlayed + recents).distinctBy { it.id }.take(16) }
    val pages = remember(quick) { quick.chunked(4) }
    val shortcuts = remember(recents, mostPlayed) { (mostPlayed + recents).distinctBy { it.albumId }.take(9) }
    val artists = remember(songs) { songs.groupBy { it.artist }.toList().sortedBy { it.first } }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF2B1457), Bg), startY = 0f, endY = 1300f)
        )
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Mi Música", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { open(Overlay.Settings) }) { Icon(Icons.Default.Settings, "Ajustes") }
                }
            }

            item {
                val chips = listOf<Pair<String, () -> Unit>>(
                    "Aleatorio" to { vm.shufflePlay(songs) },
                    "Me gusta" to { open(Overlay.Detail("Me gusta", songs.filter { it.id in vm.favorites })) },
                    "Recientes" to { open(Overlay.Detail("Agregadas recientemente", recents)) },
                    "Más escuchadas" to { open(Overlay.Detail("Más escuchadas", mostPlayed)) }
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chips) { (label, action) -> Chip(label, action) }
                }
            }

            if (songs.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text("No se encontraron canciones", color = Muted)
                    }
                }
            } else {
                item {
                    SectionHeader("Selección rápida") {
                        Box(
                            Modifier.clip(RoundedCornerShape(50))
                                .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(50))
                                .clickable { vm.play(quick, 0) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) { Text("Reproducir todo", fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                    }
                }
                item {
                    LazyRow {
                        items(pages) { page ->
                            Column(Modifier.fillParentMaxWidth(0.9f)) {
                                page.forEach { s ->
                                    SongRow(s, vm, onDelete, big = true) { vm.play(quick, quick.indexOf(s)) }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        Modifier.padding(start = 16.dp, top = 32.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("TU BIBLIOTECA", color = Muted, fontSize = 13.sp)
                            Text("Accesos directos", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        shortcuts.chunked(3).forEach { r ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                r.forEach { s ->
                                    ShortcutCard(s, Modifier.weight(1f)) {
                                        vm.play(shortcuts, shortcuts.indexOf(s))
                                    }
                                }
                                repeat(3 - r.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }

                item { SectionHeader("Agregadas recientemente") }
                item {
                    val shown = recents.take(15)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(shown) { s -> SongCard(s) { vm.play(shown, shown.indexOf(s)) } }
                    }
                }

                item { SectionHeader("Tus artistas") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(artists.take(30)) { (name, list) ->
                            Column(
                                Modifier.width(100.dp).clickable { open(Overlay.Detail(name, list)) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Art(list.first(), Modifier.size(100.dp).clip(CircleShape))
                                Text(
                                    name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ---------------------------------------------------------------- biblioteca

@Composable
private fun LibraryScreen(vm: PlayerViewModel, onDelete: (Song) -> Unit, open: (Overlay) -> Unit) {
    var sub by remember { mutableIntStateOf(0) }
    val songs = vm.songs
    val albums = remember(songs) { songs.groupBy { it.albumId }.values.toList() }
    val artists = remember(songs) { songs.groupBy { it.artist }.toList().sortedBy { it.first } }
    val favs = remember(songs, vm.favorites) { songs.filter { it.id in vm.favorites } }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Biblioteca", fontSize = 24.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )
        TabRow(selectedTabIndex = sub, containerColor = Bg, contentColor = Color.White) {
            listOf("Canciones", "Álbumes", "Artistas", "Me gusta").forEachIndexed { i, t ->
                Tab(selected = sub == i, onClick = { sub = i }, text = { Text(t, fontSize = 13.sp, maxLines = 1) })
            }
        }
        if (songs.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No se encontraron canciones", color = Muted)
            }
        } else when (sub) {
            0 -> Column(Modifier.weight(1f)) {
                Box(Modifier.padding(16.dp)) {
                    Pill("Reproducir aleatorio", Icons.Default.Shuffle) { vm.shufflePlay(songs) }
                }
                SongList(songs, vm, onDelete, Modifier.weight(1f)) { vm.play(songs, it) }
            }
            1 -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(albums) { list ->
                    Column(Modifier.clickable { open(Overlay.Detail(list.first().album, list)) }) {
                        Art(list.first(), Modifier.fillMaxWidth().aspectRatio(1f))
                        Text(list.first().album, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        Text(list.first().artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            2 -> LazyColumn(Modifier.weight(1f)) {
                items(artists) { (name, list) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { open(Overlay.Detail(name, list)) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Art(list.first(), Modifier.size(52.dp).clip(CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${list.size} canciones", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
            else -> if (favs.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Aún no tienes canciones con Me gusta", color = Muted)
                }
            } else {
                SongList(favs, vm, onDelete, Modifier.weight(1f)) { vm.play(favs, it) }
            }
        }
    }
}

// ---------------------------------------------------------------- pantallas secundarias

@Composable
private fun DetailScreen(o: Overlay.Detail, vm: PlayerViewModel, onDelete: (Song) -> Unit, onBack: () -> Unit) {
    val ids = remember(vm.songs) { vm.songs.map { it.id }.toSet() }
    val list = o.songs.filter { it.id in ids }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(o.title, onBack)
        if (list.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Aún no hay canciones aquí", color = Muted)
            }
        } else {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Reproducir", Icons.Default.PlayArrow) { vm.play(list, 0) }
                Pill("Aleatorio", Icons.Default.Shuffle, filled = false) { vm.shufflePlay(list) }
            }
            SongList(list, vm, onDelete, Modifier.weight(1f)) { vm.play(list, it) }
        }
    }
}

@Composable
private fun SearchScreen(vm: PlayerViewModel, onDelete: (Song) -> Unit, showBack: Boolean = true, onBack: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val results = remember(q, vm.songs) {
        if (q.isBlank()) emptyList()
        else vm.songs.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showBack) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") }
            TextField(
                value = q,
                onValueChange = { q = it },
                singleLine = true,
                placeholder = { Text("Buscar canciones, artistas, álbumes") },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
        }
        if (q.isNotBlank() && results.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Sin resultados", color = Muted)
            }
        } else {
            SongList(results, vm, onDelete, Modifier.weight(1f)) { vm.play(results, it) }
        }
    }
}

@Composable
private fun HiddenScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val folders = vm.hiddenFolders.toList()
    val hidden = vm.hiddenSongs
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Canciones ocultas", onBack)
        LazyColumn(Modifier.weight(1f)) {
            if (folders.isEmpty() && hidden.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text("No tienes nada oculto", color = Muted)
                    }
                }
            }
            if (folders.isNotEmpty()) {
                item { SectionTitle("Carpetas ocultas") }
                items(folders) { f ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(f, modifier = Modifier.weight(1f), fontSize = 14.sp)
                        TextButton(onClick = { vm.unhideFolder(f) }) { Text("Mostrar") }
                    }
                }
            }
            if (hidden.isNotEmpty()) {
                item { SectionTitle("Canciones ocultas") }
                items(hidden) { s ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Art(s, Modifier.size(48.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = { vm.unhideSong(s) }) { Text("Mostrar") }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- reproductores

@Composable
private fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit) {
    val s = vm.current ?: return
    Column(Modifier.fillMaxWidth().background(Surface1).clickable { onOpen() }) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(s, Modifier.size(52.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Text(s.artist, color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { vm.togglePlay() }) {
                Icon(if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Reproducir/Pausar")
            }
            IconButton(onClick = { vm.next() }) { Icon(Icons.Default.SkipNext, "Siguiente") }
        }
        LinearProgressIndicator(
            progress = { if (vm.durationMs > 0) vm.positionMs.toFloat() / vm.durationMs else 0f },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = Color.White,
            trackColor = Color(0xFF444444)
        )
    }
}

@Composable
private fun FullPlayer(vm: PlayerViewModel, onClose: () -> Unit) {
    val s = vm.current ?: return
    var dragging by remember { mutableStateOf<Float?>(null) }
    val max = vm.durationMs.coerceAtLeast(1L).toFloat()
    val fav = s.id in vm.favorites

    Column(
        Modifier.fillMaxSize().background(Bg).clickable(enabled = false) {}.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth()) {
            IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, "Cerrar", modifier = Modifier.size(32.dp)) }
        }
        Spacer(Modifier.height(16.dp))
        Art(s, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(28.dp))
        Text(s.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
        Text(s.artist, color = Muted, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Slider(
            value = dragging ?: vm.positionMs.toFloat().coerceIn(0f, max),
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { vm.seekTo(it.toLong()) }; dragging = null },
            valueRange = 0f..max,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt((dragging ?: vm.positionMs.toFloat()).toLong()), color = Muted, fontSize = 12.sp)
            Text(fmt(vm.durationMs), color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.toggleShuffle() }) {
                Icon(Icons.Default.Shuffle, "Aleatorio", tint = if (vm.shuffle) Accent else Muted)
            }
            IconButton(onClick = { vm.previous() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipPrevious, "Anterior", modifier = Modifier.size(36.dp))
            }
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Color.White).clickable { vm.togglePlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Reproducir/Pausar", tint = Color.Black, modifier = Modifier.size(40.dp)
                )
            }
            IconButton(onClick = { vm.next() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipNext, "Siguiente", modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = { vm.toggleFavorite(s) }) {
                Icon(
                    if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "Me gusta", tint = if (fav) Accent else Muted
                )
            }
        }
    }
}

// ---------------------------------------------------------------- ajustes

private fun durLabel(sec: Int): String =
    if (sec < 60) "$sec segundos"
    else if (sec % 60 == 0) "${sec / 60} min"
    else "${sec / 60} min ${sec % 60} s"

@Composable
private fun SettingsScreen(vm: PlayerViewModel, open: (Overlay) -> Unit, onBack: () -> Unit) {
    var dur by remember { mutableFloatStateOf(vm.minDurationSec.toFloat()) }
    var size by remember { mutableFloatStateOf(vm.minSizeKb / 1024f) }
    val sliderColors = SliderDefaults.colors(
        thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0xFF555555)
    )

    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenHeader("Ajustes", onBack) }
        item {
            Text(
                "Se muestran ${vm.songs.size} de ${vm.allSongs.size} audios encontrados",
                color = Muted, modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item { SectionTitle("Duración mínima") }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(if (dur < 1f) "Mostrar todas" else "Ocultar las de menos de ${durLabel(dur.toInt())}")
                Slider(
                    value = dur,
                    onValueChange = { dur = it },
                    onValueChangeFinished = { vm.setMinDuration(dur.toInt()) },
                    valueRange = 0f..300f,
                    steps = 29,
                    colors = sliderColors
                )
            }
        }

        item { SectionTitle("Tamaño mínimo") }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(if (size < 0.1f) "Mostrar todas" else "Ocultar las de menos de ${"%.1f".format(size)} MB")
                Slider(
                    value = size,
                    onValueChange = { size = it },
                    onValueChangeFinished = { vm.setMinSize((size * 1024).toInt()) },
                    valueRange = 0f..10f,
                    steps = 19,
                    colors = sliderColors
                )
            }
        }

        item { SectionTitle("Reproducción") }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Seguir con otras canciones")
                    Text(
                        "Al terminar una lista (álbum, artista, accesos directos) continúa con el resto de tu música",
                        color = Muted, fontSize = 12.sp
                    )
                }
                Switch(
                    checked = vm.autoContinue,
                    onCheckedChange = { vm.setAutoContinue(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White, checkedTrackColor = Accent, checkedBorderColor = Accent,
                        uncheckedThumbColor = Muted, uncheckedTrackColor = Surface1, uncheckedBorderColor = Muted
                    )
                )
            }
        }

        item { SectionTitle("Formatos de archivo") }
        items(AUDIO_FORMATS) { f ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(f, modifier = Modifier.weight(1f))
                Switch(
                    checked = f !in vm.disabledFormats,
                    onCheckedChange = { vm.toggleFormat(f) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Accent,
                        checkedBorderColor = Accent,
                        uncheckedThumbColor = Muted,
                        uncheckedTrackColor = Surface1,
                        uncheckedBorderColor = Muted
                    )
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
        item {
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Pill("Canciones ocultas", Icons.Default.VisibilityOff, filled = false, modifier = Modifier.fillMaxWidth()) {
                    open(Overlay.Hidden)
                }
                Pill("Restablecer filtros", Icons.Default.Refresh, filled = false, modifier = Modifier.fillMaxWidth()) {
                    vm.resetFilters(); dur = 0f; size = 0f
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
