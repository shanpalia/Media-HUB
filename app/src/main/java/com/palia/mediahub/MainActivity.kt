package com.palia.mediahub

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Color as AndroidColor
import androidx.core.view.WindowCompat
import android.os.Environment
import android.webkit.URLUtil
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
    val audioId: String?
)
private data class AudioChoice(val bitrate: Int, val label: String)
private data class LocalDownload(val id: Long, val title: String, val state: String, val uri: Uri?)

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
        Box(Modifier.size(112.dp).background(PaleMint, RoundedCornerShape(32.dp)), contentAlignment = Alignment.Center) {
            Text("M", color = DeepMint, fontSize = 76.sp, fontWeight = FontWeight.Black)
        }
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
                modifier = Modifier.padding(padding), url = url, onUrl = { url = it; chosenPlatform = detectPlatform(it) ?: chosenPlatform; resetResult() },
                platform = chosenPlatform, onPlatform = { chosenPlatform = it },
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
                        if (loadedInfo != null) mediaMode = "video"
                    }
                },
                onSelectVideo = { selectedVideo = it; selectedAudio = null; mediaMode = "video" },
                onSelectAudio = { selectedAudio = it; selectedVideo = null; mediaMode = "audio" },
                onDownload = {
                    val current = info
                    if (current != null) {
                        val format = if (mediaMode == "audio") current.audioId else selectedVideo?.id
                        if (format.isNullOrBlank()) message = "Please select an available format first."
                        else scope.launch {
                            busy = true
                            message = withContext(Dispatchers.IO) {
                                startDownload(context, url, current.title, format, mediaMode, selectedAudio?.bitrate ?: 128)
                            }
                            busy = false; refresh()
                        }
                    }
                },
                downloads = downloads, onAll = { tab = "Downloads" }
            )
            "Downloads", "History" -> DownloadsScreen(downloads, tab == "History", Modifier.padding(padding), onOpen = { selectedFile = it; tab = "Player" }, onRefresh = { refresh() })
            "Player" -> PlayerScreen(downloads, selectedFile, Modifier.padding(padding), onSelect = { selectedFile = it })
            else -> SettingsScreen(dark, { dark = it }, Modifier.padding(padding))
        }
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
    val audioChoices = listOf(AudioChoice(48, "48 kbps · MP3"), AudioChoice(128, "128 kbps · MP3"), AudioChoice(256, "256 kbps · MP3"), AudioChoice(320, "320 kbps · MP3"))
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth().background(Color.White).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).background(PaleMint, RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) { Text("M", color = DeepMint, fontSize = 36.sp, fontWeight = FontWeight.Black) }
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
                Text("Download Videos & Music", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                Text("Paste a supported public link and choose your format.", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Fast", "HD options", "No login").forEach { label -> Surface(color = Color.White, shape = CircleShape) { Text(label, Modifier.padding(horizontal = 11.dp, vertical = 8.dp), color = DeepMint, fontSize = 11.sp, fontWeight = FontWeight.Bold) } }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 14.dp).background(Color.White, RoundedCornerShape(22.dp)).padding(15.dp)) {
                Text("Supported platforms", fontWeight = FontWeight.Bold, color = Ink, fontSize = 17.sp)
                Spacer(Modifier.height(10.dp))
                platforms.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        row.forEach { name ->
                            val active = platform.equals(name, true)
                            Surface(modifier = Modifier.weight(1f).clickable { onPlatform(name) }, color = if (active) PaleMint else Color(0xFFF7FAF8), shape = RoundedCornerShape(13.dp), border = androidx.compose.foundation.BorderStroke(if (active) 1.5.dp else 1.dp, if (active) Mint else Color(0xFFE3EEE8))) {
                                Column(Modifier.padding(vertical = 11.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(platformMark(name), color = DeepMint, fontWeight = FontWeight.Black, fontSize = 19.sp)
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
        item {
            Column(Modifier.padding(horizontal = 14.dp).background(Color.White, RoundedCornerShape(22.dp)).padding(15.dp)) {
                Text("Paste video link", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = url, onValueChange = onUrl, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Paste Facebook, Instagram, TikTok link…") }, leadingIcon = { Icon(Icons.Default.Link, null, tint = DeepMint) }, trailingIcon = { if (url.isNotBlank()) IconButton(onClick = { onUrl("") }) { Icon(Icons.Default.Close, "Clear") } }, shape = RoundedCornerShape(15.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true)
                Spacer(Modifier.height(10.dp))
                Button(onClick = onLoad, enabled = !busy && url.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = DeepMint)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Icon(Icons.Default.Search, null)
                    Spacer(Modifier.width(8.dp)); Text("Load Video", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                if (message.isNotBlank()) Text(message, Modifier.padding(top = 10.dp), color = DeepMint, fontSize = 12.sp)
            }
        }
        if (info != null) {
            item {
                Column(Modifier.padding(horizontal = 14.dp).background(Color.White, RoundedCornerShape(22.dp)).padding(15.dp)) {
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
                        if (info.audioId == null) Text("Audio format is not available for this link.", color = Muted, fontSize = 12.sp)
                        audioChoices.forEach { choice ->
                            Row(Modifier.fillMaxWidth().clickable { onSelectAudio(choice) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selectedAudio?.bitrate == choice.bitrate, onClick = { onSelectAudio(choice) }, colors = RadioButtonDefaults.colors(selectedColor = DeepMint))
                                Text(choice.label, color = Ink, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Button(onClick = onDownload, enabled = !busy && if (mode == "video") selectedVideo != null else info.audioId != null && selectedAudio != null, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint), shape = RoundedCornerShape(15.dp)) {
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
private fun DownloadsScreen(items: List<LocalDownload>, history: Boolean, modifier: Modifier, onOpen: (LocalDownload) -> Unit, onRefresh: () -> Unit) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(if (history) "Download History" else "Downloads", Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink); IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh", tint = DeepMint) } }
        if (items.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No downloads yet.", color = Muted) }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) { items(items, key = { it.id }) { DownloadRow(it) { onOpen(it) } } }
    }
}

@Composable
private fun DownloadRow(item: LocalDownload, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(15.dp)).clickable(enabled = item.uri != null && item.state == "Completed", onClick = onClick).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(43.dp).background(PaleMint, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(if (item.title.endsWith(".mp3", true)) Icons.Default.MusicNote else Icons.Default.VideoFile, null, tint = DeepMint) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) { Text(item.title, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(item.state, color = Muted, fontSize = 11.sp) }
        if (item.uri != null && item.state == "Completed") Icon(Icons.Default.PlayCircle, null, tint = DeepMint)
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
private fun SettingsScreen(dark: Boolean, onDark: (Boolean) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("Settings", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(15.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Dark appearance", Modifier.weight(1f), color = Ink); Switch(dark, onDark) }
        HorizontalDivider()
        Text("Mint Mind · Version 1.0.0", Modifier.padding(top = 16.dp), color = Muted)
        Text("BY PALIAAPK HUB", Modifier.padding(top = 8.dp), color = DeepMint, fontWeight = FontWeight.Bold)
        Text("Developer by ShanPalia", color = Muted, fontSize = 12.sp)
        Text("Downloads are saved in Movies/Mint Mind.", Modifier.padding(top = 18.dp), color = Ink)
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
                val label = (if (height > 0) "${height}p" else "Video") + if (ext.isNotBlank()) " · ${ext.uppercase()}" else ""
                val size = if (f.has("filesize") && !f.isNull("filesize")) f.optLong("filesize") else if (f.has("filesize_approx")) f.optLong("filesize_approx") else 0L
                videoList.add(MediaFormat(id, label, size.takeIf { it > 0 }, ext))
            }
            val audioArray = json.optJSONArray("audio_formats")
            var audioId: String? = null
            if (audioArray != null && audioArray.length() > 0) audioId = audioArray.optJSONObject(0)?.optString("format_id")?.takeIf { !it.isNullOrBlank() && it != "null" }
            if (audioId == null) audioId = videoList.firstOrNull()?.id
            val duration = json.optDouble("duration", 0.0).toInt()
            MediaInfo(json.optString("title", "Mint Mind video"), json.optString("thumbnail", ""), duration, json.optString("extractor", platform), videoList.distinctBy { it.id }, audioId) to "Video found. Choose a format to continue."
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
        val filename = URLUtil.guessFileName(title + if (mediaType == "audio") ".mp3" else ".mp4", null, if (mediaType == "audio") "audio/mpeg" else "video/mp4")
        val request = DownloadManager.Request(uri).setTitle(filename).setDescription("Mint Mind · By PaliaAPK HUB")
            .setMimeType(if (mediaType == "audio") "audio/mpeg" else "video/mp4")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "Mint Mind/$filename")
            .setAllowedOverMetered(true).setAllowedOverRoaming(false)
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        "Download started. Check the Downloads tab or notification."
    } catch (e: Exception) { "Download error: ${e.localizedMessage ?: "Could not start download."}" }
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
        while (it.moveToNext()) {
            val state = when (it.getInt(statusCol)) {
                DownloadManager.STATUS_SUCCESSFUL -> "Completed"
                DownloadManager.STATUS_FAILED -> "Failed"
                DownloadManager.STATUS_RUNNING -> "Downloading"
                DownloadManager.STATUS_PAUSED -> "Paused"
                else -> "Queued"
            }
            val local = if (uriCol >= 0) it.getString(uriCol) else null
            result.add(LocalDownload(it.getLong(idCol), it.getString(titleCol) ?: "Media file", state, local?.let(Uri::parse)))
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
private fun platformMark(name: String) = when (name) { "Facebook" -> "f"; "Instagram" -> "◎"; "Threads" -> "@"; "TikTok" -> "♪"; "X" -> "X"; "Reddit" -> "r/"; else -> "P" }
private fun formatBytes(bytes: Long): String = if (bytes < 1024L * 1024L) "${bytes / 1024} KB" else String.format("%.1f MB", bytes / 1024.0 / 1024.0)
