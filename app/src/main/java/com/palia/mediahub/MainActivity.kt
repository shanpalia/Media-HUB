package com.palia.mediahub

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.URLUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Mint = Color(0xFF08A66A)
private val DeepMint = Color(0xFF007C50)
private val PaleMint = Color(0xFFE1F8EF)
private val Ink = Color(0xFF17231F)
private val Muted = Color(0xFF68766F)

data class DownloadItem(val id: Long, val title: String, val status: String, val uri: Uri?)
data class ResolvedMedia(val title: String, val thumbnail: String, val duration: String, val extractor: String, val formats: List<String>)

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  installSplashScreen()
  super.onCreate(savedInstanceState)
  val shared = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty() else ""
  setContent { MediaHubRoot(shared) }
 }
}

@Composable private fun MediaHubRoot(sharedText: String) {
 var showSplash by remember { mutableStateOf(true) }
 LaunchedEffect(Unit) { delay(1400); showSplash = false }
 MaterialTheme(colorScheme = lightColorScheme(primary = Mint, secondary = DeepMint, background = Color(0xFFF8FCFA), surface = Color.White, onSurface = Ink)) {
  if (showSplash) SplashScreen() else MainShell(sharedText)
 }
}

@Composable private fun SplashScreen() {
 Column(Modifier.fillMaxSize().background(Color.White).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
  BrandMark(112)
  Spacer(Modifier.height(22.dp))
  Text("Media HUB", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Ink)
  Text("Video Downloader", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = DeepMint)
  Spacer(Modifier.height(14.dp))
  Text("BY PALIAAPK HUB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted, letterSpacing = 2.sp)
  Spacer(Modifier.height(38.dp))
  CircularProgressIndicator(color = Mint, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
 }
}

@Composable private fun BrandMark(size: Int) {
 Box(Modifier.size(size.dp).background(PaleMint, RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
  Image(painterResource(R.drawable.brand_mark), contentDescription = "Media HUB logo", modifier = Modifier.fillMaxSize().padding((size * .04).dp), contentScale = ContentScale.Fit)
 }
}

@Composable private fun MainShell(sharedText: String) {
 var selected by remember { mutableStateOf("Home") }
 var url by remember { mutableStateOf(sharedText) }
 var notice by remember { mutableStateOf("") }
 var loading by remember { mutableStateOf(false) }
 var resolved by remember { mutableStateOf<ResolvedMedia?>(null) }
 var downloads by remember { mutableStateOf(emptyList<DownloadItem>()) }
 var selectedFile by remember { mutableStateOf<DownloadItem?>(null) }
 val context = LocalContext.current
 val scope = rememberCoroutineScope()
 fun refresh() { downloads = readDownloads(context) }
 LaunchedEffect(selected) { refresh() }
 Scaffold(containerColor = Color(0xFFF8FCFA), bottomBar = {
  NavigationBar(containerColor = Color.White) {
   listOf(Triple("Home", Icons.Default.Home, "Home"), Triple("Downloads", Icons.Default.Download, "Downloads"), Triple("Player", Icons.Default.PlayCircle, "Player"), Triple("History", Icons.Default.History, "History"), Triple("Settings", Icons.Default.Settings, "Settings")).forEach { (label, icon, _) ->
    NavigationBarItem(selected == label, { selected = label }, icon = { Icon(icon, null) }, label = { Text(label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = DeepMint, selectedTextColor = DeepMint, indicatorColor = PaleMint))
   }
  }
 }) { padding ->
  when (selected) {
   "Home" -> HomePage(url, { url = it; resolved = null; notice = "" }, {
    scope.launch {
     loading = true; notice = "Loading post details..."
     val result = withContext(Dispatchers.IO) { resolveMedia(url) }
     resolved = result.first
     notice = result.second
     loading = false
    }
   }, { media ->
    scope.launch {
     loading = true
     notice = withContext(Dispatchers.IO) { startMediaDownload(context, url, media.title) }
     loading = false
     refresh()
    }
   }, downloads, { selected = "Downloads" }, resolved, loading, notice, Modifier.padding(padding))
   "Downloads", "History" -> DownloadsPage(downloads, selected == "History", { item -> selectedFile = item; selected = "Player" }, Modifier.padding(padding))
   "Player" -> PlayerPage(downloads, selectedFile, { selectedFile = it }, Modifier.padding(padding))
   else -> SettingsPage(Modifier.padding(padding))
  }
 }
}

@Composable private fun HomePage(url: String, onUrl: (String) -> Unit, onLoad: () -> Unit, onDownload: (ResolvedMedia) -> Unit, downloads: List<DownloadItem>, onAll: () -> Unit, media: ResolvedMedia?, loading: Boolean, notice: String, modifier: Modifier) {
 LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
  item {
   Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
    BrandMark(58); Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
     Row(verticalAlignment = Alignment.CenterVertically) { Text("Media ", fontSize = 25.sp, fontWeight = FontWeight.Black, color = Ink); Text("HUB", fontSize = 25.sp, fontWeight = FontWeight.Black, color = DeepMint) }
     Text("Threads & Social Video Downloader", color = DeepMint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
     Text("BY PALIAAPK HUB", color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp)
    }
    Icon(Icons.Default.AccountCircle, null, tint = DeepMint, modifier = Modifier.size(32.dp))
   }
  }
  item {
   Column(Modifier.fillMaxWidth().background(PaleMint).padding(horizontal = 20.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Text("Download Threads Videos", fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, color = Ink, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text("Save public videos from Threads in a few simple steps.", color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
     FeatureTile("1", "Paste link", Modifier.weight(1f))
     FeatureTile("2", "Load post", Modifier.weight(1f))
     FeatureTile("3", "Download", Modifier.weight(1f))
    }
   }
  }
  item {
   Column(Modifier.padding(horizontal = 16.dp).background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp)) {
    Text("Paste Threads link", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(value = url, onValueChange = onUrl, modifier = Modifier.fillMaxWidth(), placeholder = { Text("https://www.threads.com/@.../post/...") }, leadingIcon = { Icon(Icons.Default.Link, null, tint = DeepMint) }, trailingIcon = { if (url.isNotBlank()) IconButton(onClick = { onUrl("") }) { Icon(Icons.Default.Close, "Clear") } }, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Mint, unfocusedBorderColor = Color(0xFFDCE8E2), focusedContainerColor = Color.White, unfocusedContainerColor = Color.White))
    Spacer(Modifier.height(12.dp))
    Button(onClick = onLoad, enabled = !loading && url.isNotBlank(), modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = DeepMint)) {
     if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Icon(Icons.Default.Search, null)
     Spacer(Modifier.width(9.dp)); Text("Load Videos", fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
    if (notice.isNotBlank()) Text(notice, color = DeepMint, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
   }
  }
  if (media != null) item {
   Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(16.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
     Box(Modifier.size(46.dp).background(PaleMint, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.VideoLibrary, null, tint = DeepMint, modifier = Modifier.size(27.dp)) }
     Spacer(Modifier.width(12.dp))
     Column(Modifier.weight(1f)) {
      Text("Post found", color = DeepMint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
      Text(media.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2)
     }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
     AssistChip(onClick = {}, label = { Text(media.extractor.ifBlank { "Social video" }) }, leadingIcon = { Icon(Icons.Default.Public, null) })
     if (media.duration.isNotBlank()) AssistChip(onClick = {}, label = { Text(media.duration) }, leadingIcon = { Icon(Icons.Default.Schedule, null) })
    }
    Text("Available video formats: " + media.formats.size, color = Muted, fontSize = 12.sp)
    Spacer(Modifier.height(12.dp))
    Button(onClick = { onDownload(media) }, enabled = !loading, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint)) {
     Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Download Video", fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
   }
  }
  item {
   Text("How it works", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 18.dp))
   Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FeatureTile("↗", "Copy public post link", Modifier.weight(1f))
    FeatureTile("⌕", "Tap Load Videos", Modifier.weight(1f))
    FeatureTile("↓", "Save to phone", Modifier.weight(1f))
   }
  }
  item {
   Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
    Text("Recent Downloads", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
    Text("View All  ›", color = DeepMint, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onAll() })
   }
  }
  if (downloads.isEmpty()) item { Text("Your downloaded videos will appear here.", color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), textAlign = TextAlign.Center) }
  else items(downloads.take(4), key = { it.id }) { DownloadRow(it) }
  item { Text("Only public and authorized content is supported. Private, login-only and DRM-protected posts cannot be downloaded.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 18.dp)) }
 }
}

@Composable private fun PlatformCard(mark: String, name: String, subtitle: String, modifier: Modifier) {
 Column(modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(vertical = 12.dp, horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
  Box(Modifier.size(38.dp).background(PaleMint, CircleShape), contentAlignment = Alignment.Center) { Text(mark, color = DeepMint, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
  Spacer(Modifier.height(5.dp)); Text(name, fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.Center); Text(subtitle, color = Muted, fontSize = 8.sp, textAlign = TextAlign.Center, lineHeight = 10.sp)
 }
}
@Composable private fun FeatureTile(mark: String, label: String, modifier: Modifier) {
 Column(modifier.background(PaleMint, RoundedCornerShape(16.dp)).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(mark, color = Mint, fontSize = 24.sp, fontWeight = FontWeight.Black); Text(label, color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 12.sp) }
}
@Composable private fun DownloadsPage(downloads: List<DownloadItem>, history: Boolean, onOpen: (DownloadItem) -> Unit, modifier: Modifier) {
 Column(modifier.fillMaxSize().padding(18.dp)) {
  Text(if (history) "Download History" else "Your Downloads", fontSize = 24.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp))
  if (downloads.isEmpty()) Text("अभी कोई डाउनलोड उपलब्ध नहीं है।", color = Muted)
  else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(downloads, key = { it.id }) { item -> DownloadRow(item) { onOpen(item) } } }
 }
}
@Composable private fun DownloadRow(item: DownloadItem, onClick: () -> Unit = {}) {
 Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).clickable(enabled = item.status == "Completed" && item.uri != null, onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
  Box(Modifier.size(44.dp).background(PaleMint, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.VideoFile, null, tint = DeepMint) }
  Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1); Text(item.status, color = Muted, fontSize = 11.sp) }
  Icon(Icons.Default.PlayCircle, null, tint = DeepMint)
 }
}
@Composable private fun PlayerPage(downloads: List<DownloadItem>, selected: DownloadItem?, onSelect: (DownloadItem) -> Unit, modifier: Modifier) {
 val context = LocalContext.current
 val playable = downloads.filter { it.status == "Completed" && it.uri != null }
 val player = remember { ExoPlayer.Builder(context).build() }
 androidx.compose.runtime.DisposableEffect(player) { onDispose { player.release() } }
 LaunchedEffect(selected?.uri) {
  selected?.uri?.let { uri ->
   player.setMediaItem(ExoMediaItem.fromUri(uri))
   player.prepare()
   player.playWhenReady = true
  }
 }
 Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
  Text("Media HUB Player", fontSize = 23.sp, fontWeight = FontWeight.Bold)
  if (selected?.uri != null) {
   AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } }, update = { it.player = player }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black, RoundedCornerShape(16.dp)))
   Text(selected.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
  } else {
   Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF10251D), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.size(64.dp)); Text("Select a downloaded video", color = Color.White) }
   }
  }
  Text("Downloaded videos", fontWeight = FontWeight.Bold, fontSize = 18.sp)
  if (playable.isEmpty()) Text("Playback के लिए पहले direct MP4 video download करें।", color = Muted)
  else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(playable, key = { it.id }) { item ->
   Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).clickable { onSelect(item) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(Icons.Default.PlayCircle, null, tint = DeepMint); Spacer(Modifier.width(10.dp)); Column { Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1); Text("Tap to play", color = Muted, fontSize = 11.sp) }
   }
  } }
 }
}
@Composable private fun SettingsPage(modifier: Modifier) {
 Column(modifier.fillMaxSize().padding(22.dp)) {
  Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(16.dp))
  Text("Media HUB  •  Version 1.0.0", color = Muted); Spacer(Modifier.height(8.dp))
  Text("BY PALIAAPK HUB", color = DeepMint, fontWeight = FontWeight.Bold); Spacer(Modifier.height(18.dp))
  Text("Downloads folder: Movies/MediaHUB", color = Ink); Text("Theme: White + Mint Green", color = Ink)
 }
}

private fun resolveMedia(raw: String): Pair<ResolvedMedia?, String> {
 val value = raw.trim()
 if (value.isBlank()) return null to "Paste a post link first."
 val parsed = Uri.parse(value)
 if ((parsed.scheme != "https" && parsed.scheme != "http") || parsed.host.isNullOrBlank()) return null to "Enter a valid public post URL."
 return try {
  val client = OkHttpClient.Builder().callTimeout(100, TimeUnit.SECONDS).build()
  val infoUrl = Uri.parse("https://media-hub-resolver.onrender.com/info").buildUpon().appendQueryParameter("url", value).build().toString()
  client.newCall(Request.Builder().url(infoUrl).build()).execute().use { response ->
   val body = response.body?.string().orEmpty()
   if (!response.isSuccessful) return null to "Could not load post: " + runCatching { JSONObject(body).optString("detail") }.getOrDefault("Post unavailable or unsupported")
   val info = JSONObject(body)
   val formats = mutableListOf<String>()
   val array = info.optJSONArray("formats")
   if (array != null) for (i in 0 until array.length()) {
    val f = array.optJSONObject(i) ?: continue
    val h = f.optInt("height", 0)
    val ext = f.optString("ext", "")
    formats.add((if (h > 0) "${h}p" else "Video") + if (ext.isNotBlank()) " · " + ext else "")
   }
   ResolvedMedia(info.optString("title", "Threads video"), info.optString("thumbnail", ""), info.optString("duration").takeIf { it != "null" }.orEmpty(), info.optString("extractor", "Threads"), formats.distinct()) to "Post loaded. Ready to download."
  }
 } catch (e: Exception) { null to "Load error: " + (e.localizedMessage ?: "Resolver server timed out") }
}

private fun startMediaDownload(context: Context, raw: String, title: String): String {
 return try {
  val downloadUri = Uri.parse("https://media-hub-resolver.onrender.com/download").buildUpon().appendQueryParameter("url", raw.trim()).build()
  val filename = URLUtil.guessFileName(title + ".mp4", null, "video/mp4")
  val request = DownloadManager.Request(downloadUri).setTitle(title).setDescription("Media HUB video download").setMimeType("video/mp4")
   .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
   .setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "MediaHUB/" + filename)
   .setAllowedOverMetered(true).setAllowedOverRoaming(false)
  (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
  "Download added. Check notification and Downloads tab."
 } catch (e: Exception) { "Download error: " + (e.localizedMessage ?: "Could not start download") }
}
private fun readDownloads(context: Context): List<DownloadItem> {
 val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
 val cursor = manager.query(DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PAUSED or DownloadManager.STATUS_SUCCESSFUL or DownloadManager.STATUS_FAILED)) ?: return emptyList()
 val result = mutableListOf<DownloadItem>()
 cursor.use {
  val idCol = it.getColumnIndex(DownloadManager.COLUMN_ID); val titleCol = it.getColumnIndex(DownloadManager.COLUMN_TITLE); val statusCol = it.getColumnIndex(DownloadManager.COLUMN_STATUS); val uriCol = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
  while (it.moveToNext()) {
   val status = when (it.getInt(statusCol)) {
    DownloadManager.STATUS_SUCCESSFUL -> "Completed"
    DownloadManager.STATUS_FAILED -> "Failed"
    DownloadManager.STATUS_RUNNING -> "Downloading"
    DownloadManager.STATUS_PAUSED -> "Paused"
    else -> "Queued"
   }
   val local = if (uriCol >= 0) it.getString(uriCol) else null
   result.add(DownloadItem(it.getLong(idCol), it.getString(titleCol) ?: "Video", status, local?.let { s -> Uri.parse(s) }))
  }
 }
 return result.reversed()
}
