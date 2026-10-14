package com.mimusica.player

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()
    private var pendingDelete: Song? = null

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK) {
            if (Build.VERSION.SDK_INT < 30) {
                pendingDelete?.let {
                    try { contentResolver.delete(it.uri, null, null) } catch (_: Exception) {}
                }
            }
            vm.loadSongs()
        }
        pendingDelete = null
    }

    private fun requestDelete(song: Song) {
        pendingDelete = song
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val pi = MediaStore.createDeleteRequest(contentResolver, listOf(song.uri))
                deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } else {
                val rows = contentResolver.delete(song.uri, null, null)
                if (rows > 0) vm.loadSongs()
                else Toast.makeText(this, "No se pudo eliminar", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) {
                deleteLauncher.launch(
                    IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                )
            } else {
                Toast.makeText(this, "No se pudo eliminar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiMusicaTheme {
              WithIntro {
                val audioPerm = if (Build.VERSION.SDK_INT >= 33)
                    Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
                val perms = if (Build.VERSION.SDK_INT >= 33)
                    arrayOf(audioPerm, Manifest.permission.POST_NOTIFICATIONS) else arrayOf(audioPerm)

                var granted by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(this@MainActivity, audioPerm) ==
                            PackageManager.PERMISSION_GRANTED
                    )
                }
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { res ->
                    granted = res[audioPerm] == true
                    if (granted) vm.loadSongs()
                }

                LaunchedEffect(Unit) {
                    if (granted) vm.loadSongs() else launcher.launch(perms)
                }

                if (granted) {
                    MusicApp(vm) { song -> requestDelete(song) }
                } else {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("Necesito permiso para leer la música de tu teléfono.")
                            Button(onClick = { launcher.launch(perms) }) { Text("Dar permiso") }
                        }
                    }
                }
              }
            }
        }
    }
}
