package com.palia.mediahub

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.graphics.Color as AndroidColor
import androidx.core.view.WindowCompat
import android.os.Environment
import android.webkit.URLUtil
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val BACKEND = "https://media-hub-resolver.onrender.com"
private const val UPDATE_MANIFEST_URL = "https://paliaapkhub.com/mint-mind-version.json"
private val Mint = Color(0xFF00A979)
private val DeepMint = Color(0xFF006B50)
private val PaleMint = Color(0xFFE8FFF7)
private val Ink = Color(0xFF082C28)
private val Muted = Color(0xFF60766F)

private data class MediaFormat(val id: String, val label: String, val size: Long?, val ext: String)
private data class MediaInfo(
    val title: String,
    val thumbnail: String,
    val duration: Int,
    val platform: String,
    val videos: List<MediaFormat>,
    val audioFormats: List<AudioChoice>
)
private data class AudioChoice(val id: String, val bitrate: Int, val label: String, val size: Long?)
private data class AppUpdate(val versionName: String, val versionCode: Int, val downloadUrl: String, val changelog: String)
private data class LocalDownload(val id: Long, val title: String, val state: String, val uri: Uri?, val downloaded: Long = 0L, val total: Long = -1L)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep native content clear of Android status/navigation bars.
        window.statusBarColor = AndroidColor.rgb(248, 252, 249)
        window.navigationBarColor = AndroidColor.rgb(255, 255, 255)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val shared = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty() else ""
        setContent { MintMindApp(shared) }
    }
}

@Composable
private fun MintMindApp(sharedText: String) {
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(1100); splash = false }
    MaterialTheme(colorScheme = lightColorScheme(primary = Mint, secondary = DeepMint, background = Color(0xFFF7FCF9), surface = Color.White, onSurface = Ink)) {
        if (splash) Splash() else MainScreen(sharedText)
    }
}

@Composable
private fun Splash() {
    Column(Modifier.fillMaxSize().background(Color.White), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.brand_mark), contentDescription = "Mint Mind logo", modifier = Modifier.size(124.dp))
        Spacer(Modifier.height(18.dp))
        Text("Mint Mind", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Black)
        Text("VIDEO DOWNLOADER", color = DeepMint, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Text("BY PALIAAPK HUB", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(28.dp))
        CircularProgressIndicator(color = Mint, modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun MainScreen(sharedText: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("Home") }
    var url by remember { mutableStateOf(sharedText) }
    var chosenPlatform by remember { mutableStateOf(detectPlatform(sharedText) ?: "Facebook") }
    var info by remember { mutableStateOf<MediaInfo?>(null) }
    var selectedVideo by remember { mutableStateOf<MediaFormat?>(null) }
    var selectedAudio by remember { mutableStateOf<AudioChoice?>(null) }
    var mediaMode by remember { mutableStateOf("video") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var downloads by remember { mutableStateOf(readDownloads(context)) }
    var selectedFile by remember { mutableStateOf<LocalDownload?>(null) }
    var dark by remember { mutableStateOf(false) }
    var overwriteTarget by remember { mutableStateOf<LocalDownload?>(null) }
    var renameTarget by remember { mutableStateOf<LocalDownload?>(null) }
    var renameText by remember { mutableStateOf("") }
    var actionMessage by remember { mutableStateOf("") }
    var updateBusy by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("Tap Check for updates to check PaliaAPK HUB.") }
    var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun refresh() { downloads = readDownloads(context) }
    fun resetResult() { info = null; selectedVideo = null; selectedAudio = null; message = "" }

    BackHandler(enabled = tab != "Home") { tab = "Home" }
    Scaffold(
        containerColor = if (dark) Color(0xFF102A23) else Color(0xFFF7FCF9),
        bottomBar = {
            NavigationBar(containerColor = if (dark) Color(0xFF173B31) else Color.White) {
                listOf(Triple("Home", Icons.Default.Home, "Home"), Triple("Downloads", Icons.Default.Download, "Downloads"), Triple("Player", Icons.Default.PlayCircle, "Player"), Triple("History", Icons.Default.History, "History"), Triple("Settings", Icons.Default.Settings, "Settings")).forEach { (label, icon, _) ->
                    NavigationBarItem(selected = tab == label, onClick = { tab = label; refresh() }, icon = { Icon(icon, null) }, label = { Text(label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = DeepMint, selectedTextColor = DeepMint, indicatorColor = PaleMint))
                }
            }
        }
    ) { padding ->
        when (tab) {
            "Home" -> HomeScreen(
                modifier = Modifier.padding(padding), url = url, onUrl = { url = it; chosenPlatform = detectPlatform(it).orEmpty(); resetResult() },
                platform = detectPlatform(url).orEmpty(), onPlatform = { chosenPlatform = it },
                info = info, selectedVideo = selectedVideo, selectedAudio = selectedAudio,
                mode = mediaMode, onMode = { mediaMode = it; selectedAudio = null; selectedVideo = null },
                busy = busy, message = message,
                onLoad = {
                    scope.launch {
                        busy = true; message = "Checking public video link…"; resetResult()
                        val result = withContext(Dispatchers.IO) { fetchInfo(url) }
                        val loadedInfo = result.first
                        info = loadedInfo
                        message = result.second
                        busy = false
                        if (loadedInfo != null) {
                            mediaMode = "video"
                            selectedVideo = loadedInfo.videos.firstOrNull()
                            selectedAudio = loadedInfo.audioFormats.firstOrNull()
                        }
                    }
                },
                onSelectVideo = { selectedVideo = it; selectedAudio = null; mediaMode = "video" },
                onSelectAudio = { selectedAudio = it; selectedVideo = null; mediaMode = "audio" },
                onDownload = {
                    val current = info
                    if (current != null) {
                        val format = if (mediaMode == "audio") selectedAudio?.id else (selectedVideo?.id ?: current.videos.firstOrNull()?.id)
                        if (format.isNullOrBlank()) message = "Please select an available format first."
                        else {
                            val targetName = safeFileName(current.title, if (mediaMode == "audio") ".mp3" else ".mp4")
                            val duplicate = downloads.firstOrNull { it.title.equals(targetName, true) && it.state != "Failed" }
                            if (duplicate != null) overwriteTarget = duplicate
                            else scope.launch {
                                busy = true
                                message = withContext(Dispatchers.IO) { startDownload(context, url, current.title, format, mediaMode, selectedAudio?.bitrate ?: 128) }
                                busy = false
                                refresh()
                                tab = "Downloads"
                            }
                        }
                    }
                },
                downloads = downloads, onAll = { tab = "Downloads" }
            )
            "Downloads", "History" -> DownloadsScreen(
                downloads, tab == "History", Modifier.padding(padding),
                onOpen = { selectedFile = it; tab = "Player" },
                onDelete = { item -> (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(item.id); refresh() },
                onRename = { item -> renameTarget = item; renameText = item.title },
                onRefresh = { refresh() }
            )
            "Player" -> PlayerScreen(downloads, selectedFile, Modifier.padding(padding), onSelect = { selectedFile = it })
            else -> SettingsScreen(
                dark, { dark = it }, Modifier.padding(padding),
                updateBusy, updateMessage, availableUpdate,
                onCheckUpdate = {
                    scope.launch {
                        updateBusy = true
                        updateMessage = "Checking PaliaAPK HUB for updates…"
                        availableUpdate = null
                        val result = withContext(Dispatchers.IO) { checkForAppUpdate(context) }
                        availableUpdate = result.first
                        updateMessage = result.second
                        updateBusy = false
                    }
                }
            )
        }
        if (overwriteTarget != null) {
            AlertDialog(
                onDismissRequest = { overwriteTarget = null },
                title = { Text("File already exists") },
                text = { Text("This file already exists. Do you want to overwrite it?") },
                confirmButton = {
                    TextButton(onClick = {
                        val old = overwriteTarget
                        overwriteTarget = null
                        if (old != null) scope.launch {
                            busy = true
                            withContext(Dispatchers.IO) { (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(old.id) }
                            val current = info
                            val format = if (mediaMode == "audio") selectedAudio?.id else (selectedVideo?.id ?: current?.videos?.firstOrNull()?.id)
                            if (current != null && !format.isNullOrBlank()) {
                                message = withContext(Dispatchers.IO) { startDownload(context, url, current.title, format, mediaMode, selectedAudio?.bitrate ?: 128) }
                                tab = "Downloads"
                            }
                            busy = false
                            refresh()
                        }
                    }) { Text("Overwrite") }
                },
                dismissButton = { TextButton(onClick = { overwriteTarget = null }) { Text("Cancel") } }
            )
        }
        if (renameTarget != null) {
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text("Rename file") },
                text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, label = { Text("File name") }) },
                confirmButton = {
                    TextButton(onClick = {
                        val target = renameTarget
                        renameTarget = null
                        if (target != null) {
                            actionMessage = if (renameDownloadedFile(target, renameText)) "File renamed." else "Android could not rename this file. Storage access may be restricted."
                            refresh()
                        }
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
            )
        }
        if (actionMessage.isNotBlank()) AlertDialog(onDismissRequest = { actionMessage = "" }, confirmButton = { TextButton(onClick = { actionMessage = "" }) { Text("OK") } }, text = { Text(actionMessage) })
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier, url: String, onUrl: (String) -> Unit, platform: String, onPlatform: (String) -> Unit,
    info: MediaInfo?, selectedVideo: MediaFormat?, selectedAudio: AudioChoice?, mode: String, onMode: (String) -> Unit,
    busy: Boolean, message: String, onLoad: () -> Unit, onSelectVideo: (MediaFormat) -> Unit,
    onSelectAudio: (AudioChoice) -> Unit, onDownload: () -> Unit, downloads: List<LocalDownload>, onAll: () -> Unit
) {
    val platforms = listOf("Facebook", "Instagram", "Threads", "TikTok", "X", "Reddit", "Pinterest")
    val listState = rememberLazyListState()
    LaunchedEffect(info) { if (info != null) listState.animateScrollToItem(4) }
    LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth().background(Color.White).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.brand_mark), contentDescription = "Mint Mind logo", modifier = Modifier.size(58.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Mint Mind", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    Text("All-in-One Video Downloader", color = DeepMint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("BY PALIAAPK HUB", color = Muted, fontSize = 9.sp, letterSpacing = 1.4.sp)
                }
                Icon(Icons.Default.Verified, null, tint = Mint, modifier = Modifier.size(25.dp))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp).background(PaleMint, RoundedCornerShape(24.dp)).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, null, tint = DeepMint, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text("Download Video", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Paste a public link to continue", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 14.dp).background(Color.White, RoundedCornerShape(22.dp)).padding(15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Link, null, tint = DeepMint, modifier = Modifier.size(21.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Paste video link", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = url, onValueChange = onUrl, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Paste Facebook, Instagram, TikTok link…") }, leadingIcon = { Icon(Icons.Default.Link, null, tint = DeepMint) }, trailingIcon = { if (url.isNotBlank()) IconButton(onClick = { onUrl("") }) { Icon(Icons.Default.Close, "Clear") } }, shape = RoundedCornerShape(15.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true)
                Spacer(Modifier.height(10.dp))
                Button(onClick = onLoad, enabled = !busy && url.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = DeepMint)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(8.dp)); Text("Download", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                if (message.isNotBlank()) Text(message, Modifier.padding(top = 10.dp), color = DeepMint, fontSize = 12.sp)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 14.dp).background(Color.White, RoundedCornerShape(22.dp)).padding(15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Public, null, tint = DeepMint, modifier = Modifier.size(21.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Supported platforms", fontWeight = FontWeight.Bold, color = Ink, fontSize = 17.sp)
                }
                Spacer(Modifier.height(10.dp))
                platforms.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        row.forEach { name ->
                            val active = platform.equals(name, true)
                            Surface(modifier = Modifier.weight(1f).clickable { onPlatform(name) }, color = if (active) PaleMint else Color(0xFFF7FAF8), shape = RoundedCornerShape(13.dp), border = androidx.compose.foundation.BorderStroke(if (active) 1.5.dp else 1.dp, if (active) Mint else Color(0xFFE3EEE8))) {
                                Column(Modifier.padding(vertical = 11.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    BrandPlatformIcon(name, modifier = Modifier.size(30.dp))
                                    Text(name, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, maxLines = 1)
                                }
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(7.dp))
                }
            }
        }
        if (info != null) {
            item {
                Column(Modifier.padding(horizontal = 14.dp).background(Color.White, RoundedCornerShape(22.dp)).padding(15.dp)) {
                    VideoPreview(url)
                    Spacer(Modifier.height(12.dp))
                    Text(info.title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(info.platform + if (info.duration > 0) " · ${info.duration}s" else "", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = mode == "video", onClick = { onMode("video") }, label = { Text("Video") }, leadingIcon = { Icon(Icons.Default.VideoFile, null) })
                        FilterChip(selected = mode == "audio", onClick = { onMode("audio") }, label = { Text("Music / MP3") }, leadingIcon = { Icon(Icons.Default.MusicNote, null) })
                    }
                    if (mode == "video") {
                        Text("Video quality", color = Ink, fontWeight = FontWeight.Bold)
                        if (info.videos.isEmpty()) Text("No video formats were returned for this link.", color = Muted, fontSize = 12.sp)
                        info.videos.forEach { format ->
                            Row(Modifier.fillMaxWidth().clickable { onSelectVideo(format) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selectedVideo?.id == format.id, onClick = { onSelectVideo(format) }, colors = RadioButtonDefaults.colors(selectedColor = DeepMint))
                                Column(Modifier.weight(1f)) { Text(format.label, color = Ink, fontWeight = FontWeight.SemiBold); Text(format.size?.let { formatBytes(it) } ?: "Size not reported", color = Muted, fontSize = 11.sp) }
                            }
                        }
                    } else {
                        Text("Audio quality", color = Ink, fontWeight = FontWeight.Bold)
                        if (info.audioFormats.isEmpty()) Text("Audio format is not available for this link.", color = Muted, fontSize = 12.sp)
                        info.audioFormats.forEach { choice ->
                            Row(Modifier.fillMaxWidth().clickable { onSelectAudio(choice) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selectedAudio?.id == choice.id, onClick = { onSelectAudio(choice) }, colors = RadioButtonDefaults.colors(selectedColor = DeepMint))
                                Column(Modifier.weight(1f)) {
                                    Text(choice.label, color = Ink, fontWeight = FontWeight.SemiBold)
                                    Text(choice.size?.let { formatBytes(it) } ?: "Size unavailable", color = Muted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    Button(onClick = onDownload, enabled = !busy && if (mode == "video") (selectedVideo != null || info.videos.isNotEmpty()) else info.audioFormats.isNotEmpty() && selectedAudio != null, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint), shape = RoundedCornerShape(15.dp)) {
                        Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text(if (mode == "audio") "Download MP3" else "Download Video", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 14.dp).background(Color.White, RoundedCornerShape(20.dp)).padding(15.dp)) {
                Text("How it works", fontSize = 17.sp, color = Ink, fontWeight = FontWeight.Bold)
                Text("1. Copy a public video link\n2. Paste it and tap Load Video\n3. Choose audio or video quality\n4. Download and play from the app", color = Muted, fontSize = 13.sp, lineHeight = 22.sp)
            }
        }
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) { Text("Recent downloads", Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text("View all  ›", Modifier.clickable { onAll() }, color = DeepMint, fontWeight = FontWeight.Bold) } }
        if (downloads.isEmpty()) item { Text("Your downloaded files will appear here.", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Muted, fontSize = 12.sp) }
        else items(downloads.take(3), key = { it.id }) { DownloadRow(it) }
        item { Text("Only download content you own or have permission to save. Platform restrictions may apply.", Modifier.padding(horizontal = 18.dp), color = Muted, fontSize = 10.sp) }
    }
}

@Composable
private fun VideoPreview(url: String) {
    val context = LocalContext.current
    val player = remember(url) { ExoPlayer.Builder(context).build() }
    LaunchedEffect(url) {
        if (url.isNotBlank()) {
            val previewUri = Uri.parse("$BACKEND/preview").buildUpon().appendQueryParameter("url", url).build()
            player.setMediaItem(PlayerMediaItem.fromUri(previewUri))
            player.prepare()
            player.playWhenReady = false
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Column(Modifier.fillMaxWidth()) {
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true; setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING) } },
            update = { it.player = player },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black, RoundedCornerShape(16.dp))
        )
        Text("Video preview · Tap play", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun DownloadsScreen(
    items: List<LocalDownload>, history: Boolean, modifier: Modifier,
    onOpen: (LocalDownload) -> Unit, onDelete: (LocalDownload) -> Unit,
    onRename: (LocalDownload) -> Unit, onRefresh: () -> Unit
) {
    LaunchedEffect(history) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            onRefresh()
        }
    }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, null, tint = DeepMint, modifier = Modifier.size(25.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (history) "Download History" else "Downloads", Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh", tint = DeepMint) }
        }
        if (items.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No downloads yet.", color = Muted) }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(items, key = { it.id }) { item ->
                DownloadRow(item, onClick = { if (item.state == "Completed") onOpen(item) }, onRename = { onRename(item) }, onDelete = { onDelete(item) })
            }
        }
    }
}

@Composable
private fun DownloadRow(
    item: LocalDownload, onClick: () -> Unit = {},
    onRename: () -> Unit = {}, onDelete: () -> Unit = {}
) {
    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(15.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(43.dp).background(PaleMint, RoundedCornerShape(12.dp)).clickable(enabled = item.uri != null && item.state == "Completed", onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(if (item.title.endsWith(".mp3", true)) Icons.Default.MusicNote else Icons.Default.VideoFile, null, tint = DeepMint)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clickable(enabled = item.uri != null && item.state == "Completed", onClick = onClick)) {
            Text(item.title, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (item.total > 0L && item.state != "Completed") "${item.state} · ${((item.downloaded * 100) / item.total).coerceIn(0, 100)}% · ${formatBytes(item.downloaded)} / ${formatBytes(item.total)}"
                else if (item.downloaded > 0L && item.state != "Completed") "${item.state} · ${formatBytes(item.downloaded)}"
                else item.state,
                color = Muted, fontSize = 11.sp
            )
            if (item.state == "Downloading" || item.state == "Queued" || item.state == "Paused") {
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { if (item.total > 0L) (item.downloaded.toFloat() / item.total).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = Mint, trackColor = PaleMint
                )
            }
        }
        if (item.state == "Completed") {
            IconButton(onClick = onClick) { Icon(Icons.Default.PlayCircle, "Play", tint = DeepMint) }
            IconButton(onClick = onRename) { Icon(Icons.Default.Edit, "Rename", tint = DeepMint) }
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFCC4141)) }
    }
}

@Composable
private fun PlayerScreen(items: List<LocalDownload>, selected: LocalDownload?, modifier: Modifier, onSelect: (LocalDownload) -> Unit) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(selected?.uri) { selected?.uri?.let { player.setMediaItem(PlayerMediaItem.fromUri(it)); player.prepare(); player.playWhenReady = true } }
    val playable = items.filter { it.state == "Completed" && it.uri != null }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Mint Mind Player", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        if (selected?.uri != null) {
            AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } }, update = { it.player = player }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black, RoundedCornerShape(15.dp)))
            Text(selected.title, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 2)
        } else Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF10251D), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) { Text("Select a downloaded video or audio file", color = Color.White) }
        Text("Downloaded media", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        if (playable.isEmpty()) Text("Your completed downloads will appear here.", color = Muted)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(playable, key = { it.id }) { DownloadRow(it) { onSelect(it) } } }
    }
}

@Composable
private fun SettingsScreen(
    dark: Boolean, onDark: (Boolean) -> Unit, modifier: Modifier,
    updateBusy: Boolean, updateMessage: String, availableUpdate: AppUpdate?,
    onCheckUpdate: () -> Unit
) {
    val context = LocalContext.current
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Dark appearance", Modifier.weight(1f), color = Ink); Switch(dark, onDark) }
        HorizontalDivider()
        Text("App updates", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Current version: ${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 13.sp)
        Button(onClick = onCheckUpdate, enabled = !updateBusy, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = DeepMint)) {
            if (updateBusy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            else Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(8.dp)); Text(if (updateBusy) "Checking…" else "Check for updates")
        }
        Text(updateMessage, color = DeepMint, fontSize = 13.sp)
        if (availableUpdate != null) {
            Text("New version: ${availableUpdate.versionName}", color = Ink, fontWeight = FontWeight.Bold)
            if (availableUpdate.changelog.isNotBlank()) Text(availableUpdate.changelog, color = Muted, fontSize = 12.sp)
            Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(availableUpdate.downloadUrl))) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Download update")
            }
        }
        HorizontalDivider()
        Text("BY PALIAAPK HUB", color = DeepMint, fontWeight = FontWeight.Bold)
        Text("Developer by ShanPalia", color = Muted, fontSize = 12.sp)
        Text("Downloads are saved in Movies/Mint Mind.", color = Ink)
    }
}

private fun fetchInfo(raw: String): Pair<MediaInfo?, String> {
    val value = raw.trim()
    val platform = detectPlatform(value) ?: return null to "Enter a supported public link from Facebook, Instagram, Threads, TikTok, X, Reddit or Pinterest."
    return try {
        val client = OkHttpClient.Builder().callTimeout(100, TimeUnit.SECONDS).build()
        val uri = Uri.parse("$BACKEND/info").buildUpon().appendQueryParameter("url", value).build().toString()
        client.newCall(Request.Builder().url(uri).get().build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return null to runCatching { JSONObject(body).optString("detail", "Could not read this post.") }.getOrDefault("Could not read this post.")
            val json = JSONObject(body)
            val videoList = mutableListOf<MediaFormat>()
            val formats = json.optJSONArray("formats")
            if (formats != null) for (i in 0 until formats.length()) {
                val f = formats.optJSONObject(i) ?: continue
                val id = f.optString("format_id")
                if (id.isBlank() || id == "null") continue
                val height = f.optInt("height", 0)
                val ext = f.optString("ext", "")
                val label = f.optString("label").takeIf { it.isNotBlank() && it != "null" } ?: ((if (height > 0) "${height}p" else "Video") + if (ext.isNotBlank()) " · ${ext.uppercase()}" else "")
                val size = if (f.has("filesize") && !f.isNull("filesize")) f.optLong("filesize") else if (f.has("filesize_approx")) f.optLong("filesize_approx") else 0L
                videoList.add(MediaFormat(id, label, size.takeIf { it > 0 }, ext))
            }
            val audioArray = json.optJSONArray("audio_formats")
            val audioList = mutableListOf<AudioChoice>()
            if (audioArray != null) for (i in 0 until audioArray.length()) {
                val f = audioArray.optJSONObject(i) ?: continue
                val id = f.optString("format_id")
                if (id.isBlank() || id == "null") continue
                val bitrate = f.optDouble("abr", 128.0).toInt().coerceIn(28, 320)
                val size = if (f.has("filesize") && !f.isNull("filesize")) f.optLong("filesize").takeIf { it > 0 } else null
                val label = f.optString("label").takeIf { it.isNotBlank() && it != "null" } ?: (f.optString("ext", "Audio").uppercase() + " · " + bitrate + " kbps")
                audioList.add(AudioChoice(id, bitrate, label, size))
            }
            val duration = json.optDouble("duration", 0.0).toInt()
            MediaInfo(json.optString("title", "Mint Mind video"), json.optString("thumbnail", ""), duration, json.optString("extractor", platform), videoList.distinctBy { it.id }, audioList.distinctBy { it.id }) to "Video found. Choose a format to continue."
        }
    } catch (e: Exception) { null to "Connection error: ${e.localizedMessage ?: "Please try again."}" }
}

private fun startDownload(context: Context, raw: String, title: String, formatId: String, mediaType: String, bitrate: Int): String {
    return try {
        val uri = Uri.parse("$BACKEND/download").buildUpon()
            .appendQueryParameter("url", raw.trim())
            .appendQueryParameter("format_id", formatId)
            .appendQueryParameter("media_type", mediaType)
            .apply { if (mediaType == "audio") appendQueryParameter("audio_bitrate", bitrate.toString()) }
            .build()
        val filename = safeFileName(title, if (mediaType == "audio") ".mp3" else ".mp4")
        val request = DownloadManager.Request(uri).setTitle(filename).setDescription("Mint Mind · By PaliaAPK HUB")
            .setMimeType(if (mediaType == "audio") "audio/mpeg" else "video/mp4")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "Mint Mind/$filename")
            .setAllowedOverMetered(true).setAllowedOverRoaming(false)
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        "Download started. Check the Downloads tab or notification."
    } catch (e: Exception) { "Download error: ${e.localizedMessage ?: "Could not start download."}" }
}

private fun safeFileName(title: String, extension: String): String {
    val clean = title.replace('\n', ' ').replace('\r', ' ')
        .replace(Regex("[/:*?<>|]"), " ")
        .replace(Regex("\\s+"), " ").trim().trim('.')
        .take(100).ifBlank { "Mint Mind video" }
    return clean.removeSuffix(".mp4").removeSuffix(".mp3") + extension
}

private fun renameDownloadedFile(item: LocalDownload, requestedName: String): Boolean {
    return try {
        val oldUri = item.uri ?: return false
        if (oldUri.scheme != "file") return false
        val oldFile = java.io.File(oldUri.path ?: return false)
        val extension = oldFile.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        val base = requestedName.trim().removeSuffix(extension)
            .replace(Regex("[/:*?<>|]"), "_").take(100)
        if (base.isBlank()) return false
        val newFile = java.io.File(oldFile.parentFile, base + extension)
        if (newFile.exists()) return false
        oldFile.renameTo(newFile)
    } catch (_: Exception) { false }
}

private fun readDownloads(context: Context): List<LocalDownload> {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val cursor = manager.query(DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PAUSED or DownloadManager.STATUS_SUCCESSFUL or DownloadManager.STATUS_FAILED)) ?: return emptyList()
    val result = mutableListOf<LocalDownload>()
    cursor.use {
        val idCol = it.getColumnIndex(DownloadManager.COLUMN_ID)
        val titleCol = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
        val statusCol = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
        val uriCol = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
        val downloadedCol = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
        val totalCol = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
        while (it.moveToNext()) {
            val state = when (it.getInt(statusCol)) {
                DownloadManager.STATUS_SUCCESSFUL -> "Completed"
                DownloadManager.STATUS_FAILED -> "Failed"
                DownloadManager.STATUS_RUNNING -> "Downloading"
                DownloadManager.STATUS_PAUSED -> "Paused"
                else -> "Queued"
            }
            val local = if (uriCol >= 0) it.getString(uriCol) else null
            result.add(LocalDownload(
                it.getLong(idCol), it.getString(titleCol) ?: "Media file", state, local?.let(Uri::parse),
                if (downloadedCol >= 0) it.getLong(downloadedCol) else 0L,
                if (totalCol >= 0) it.getLong(totalCol) else -1L
            ))
        }
    }
    return result.reversed()
}

private fun detectPlatform(raw: String): String? {
    val host = runCatching { Uri.parse(raw).host?.lowercase().orEmpty() }.getOrDefault("")
    return when {
        "facebook.com" in host || host == "fb.watch" -> "Facebook"
        "instagram.com" in host -> "Instagram"
        "threads.com" in host || "threads.net" in host -> "Threads"
        "tiktok.com" in host -> "TikTok"
        host == "x.com" || "twitter.com" in host -> "X"
        "reddit.com" in host || host == "redd.it" -> "Reddit"
        "pinterest.com" in host || host == "pin.it" -> "Pinterest"
        else -> null
    }
}
@Composable
private fun BrandPlatformIcon(name: String, modifier: Modifier = Modifier) {
    val brand = when (name) {
        "Facebook" -> Color(0xFF0866FF)
        "Instagram" -> Color(0xFFE1306C)
        "Threads" -> Color(0xFF101010)
        "TikTok" -> Color(0xFF111111)
        "X" -> Color(0xFF111111)
        "Reddit" -> Color(0xFFFF4500)
        else -> Color(0xFFBD081C)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        when (name) {
            "Instagram" -> androidx.compose.foundation.Canvas(Modifier.size(26.dp)) {
                val stroke = 2.5.dp.toPx()
                drawRoundRect(color = brand, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * .28f))
                drawCircle(brand, radius = size.minDimension * .19f, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke), center = center)
                drawCircle(brand, radius = size.minDimension * .055f, center = androidx.compose.ui.geometry.Offset(size.width * .73f, size.height * .27f))
            }
            "Reddit" -> androidx.compose.foundation.Canvas(Modifier.size(28.dp)) {
                drawCircle(Color(0xFFFF4500), radius = size.minDimension * .46f)
                drawCircle(Color.White, radius = size.minDimension * .055f, center = androidx.compose.ui.geometry.Offset(size.width * .36f, size.height * .48f))
                drawCircle(Color.White, radius = size.minDimension * .055f, center = androidx.compose.ui.geometry.Offset(size.width * .64f, size.height * .48f))
                drawArc(Color.White, 15f, 150f, false, topLeft = androidx.compose.ui.geometry.Offset(size.width*.29f,size.height*.47f), size = androidx.compose.ui.geometry.Size(size.width*.42f,size.height*.30f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            }
            "TikTok" -> Box(contentAlignment = Alignment.Center) {
                Text("♪", color = Color(0xFF25F4EE), fontSize = 29.sp, fontWeight = FontWeight.Black, modifier = Modifier.offset(x = (-1).dp, y = 1.dp))
                Text("♪", color = Color(0xFFFE2C55), fontSize = 29.sp, fontWeight = FontWeight.Black, modifier = Modifier.offset(x = 1.dp, y = (-1).dp))
                Text("♪", color = Color.Black, fontSize = 29.sp, fontWeight = FontWeight.Black)
            }
            "Facebook" -> Text("f", color = brand, fontSize = 31.sp, fontWeight = FontWeight.Black)
            "Threads" -> Text("@", color = brand, fontSize = 27.sp, fontWeight = FontWeight.Medium)
            "X" -> Text("𝕏", color = brand, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            else -> Text("P", color = brand, fontSize = 29.sp, fontWeight = FontWeight.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
        }
    }
}
private fun formatBytes(bytes: Long): String = if (bytes < 1024L * 1024L) "${bytes / 1024} KB" else String.format("%.1f MB", bytes / 1024.0 / 1024.0)


private fun checkForAppUpdate(context: Context): Pair<AppUpdate?, String> {
    return try {
        val request = Request.Builder().url(UPDATE_MANIFEST_URL).header("Cache-Control", "no-cache").build()
        OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null to "Could not check updates right now. Please try again later."
            val json = JSONObject(response.body?.string().orEmpty())
            val latestCode = json.optInt("versionCode", 0)
            val latestName = json.optString("versionName", "")
            val downloadUrl = json.optString("downloadUrl", "")
            if (latestCode <= 0 || latestName.isBlank() || !downloadUrl.startsWith("https://")) {
                return null to "Update information on PaliaAPK HUB is incomplete."
            }
            val currentCode = BuildConfig.VERSION_CODE
            if (latestCode > currentCode) {
                AppUpdate(latestName, latestCode, downloadUrl, json.optString("changelog", "")) to "A new update is available."
            } else {
                null to "You are up to date. Mint Mind ${BuildConfig.VERSION_NAME} is the latest version."
            }
        }
    } catch (_: Exception) {
        null to "Unable to connect to paliaapkhub.com. Check your internet and try again."
    }
}
