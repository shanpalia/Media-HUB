package com.palia.mediahub

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.URLUtil
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Mint = Color(0xFF08A66A)
private val DeepMint = Color(0xFF007C50)
private val PaleMint = Color(0xFFE1F8EF)
private val Ink = Color(0xFF17231F)
private val Muted = Color(0xFF68766F)

data class DownloadItem(val id: Long, val title: String, val status: String, val uri: Uri?)

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val shared = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty() else ""
  setContent { MediaHubRoot(shared) }
 }
}

@Composable private fun MediaHubRoot(sharedText: String) {
 var splash by remember { mutableStateOf(true) }
 LaunchedEffect(Unit) { delay(1300); splash = false }
 MaterialTheme(colorScheme = lightColorScheme(primary = Mint, secondary = DeepMint, background = Color(0xFFF8FCFA), surface = Color.White, onSurface = Ink)) {
  if (splash) SplashScreen() else MainShell(sharedText)
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
  Icon(Icons.Default.PlayCircleFilled, null, tint = DeepMint, modifier = Modifier.size((size * .58).dp))
  Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).size((size * .28).dp).background(Mint, CircleShape), contentAlignment = Alignment.Center) {
   Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size((size * .19).dp))
  }
 }
}

@Composable private fun MainShell(sharedText: String) {
 var selected by remember { mutableStateOf("Home") }
 var url by remember { mutableStateOf(sharedText) }
 var notice by remember { mutableStateOf("") }
 var downloads by remember { mutableStateOf(emptyList<DownloadItem>()) }
 val context = LocalContext.current
 val clipboard = LocalClipboardManager.current
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
   "Home" -> HomePage(url, { url = it }, {
    url = clipboard.getText()?.text.orEmpty()
    notice = if (url.isBlank()) "Clipboard में कोई link नहीं मिला" else "Link pasted"
   }, { notice = startDownload(context, url); refresh() }, downloads, { selected = "Downloads" }, notice, Modifier.padding(padding))
   "Downloads", "History" -> DownloadsPage(downloads, selected == "History", Modifier.padding(padding))
   "Player" -> PlayerPage(downloads, Modifier.padding(padding))
   else -> SettingsPage(Modifier.padding(padding))
  }
 }
}

@Composable private fun HomePage(url: String, onUrl: (String) -> Unit, onPaste: () -> Unit, onDownload: () -> Unit, downloads: List<DownloadItem>, onAll: () -> Unit, notice: String, modifier: Modifier) {
 LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
  item {
   Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
    BrandMark(48); Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
     Row(verticalAlignment = Alignment.CenterVertically) { Text("Media ", fontSize = 23.sp, fontWeight = FontWeight.Black, color = Ink); Text("HUB", fontSize = 23.sp, fontWeight = FontWeight.Black, color = DeepMint) }
     Text("Video Downloader", color = DeepMint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
     Text("BY PALIAAPK HUB", color = Muted, fontSize = 8.sp, letterSpacing = 1.4.sp)
    }
    Icon(Icons.Default.NotificationsNone, null, tint = Ink, modifier = Modifier.size(25.dp)); Spacer(Modifier.width(12.dp))
    Icon(Icons.Default.AccountCircle, null, tint = DeepMint, modifier = Modifier.size(30.dp))
   }
  }
  item {
   Row(Modifier.fillMaxWidth().background(PaleMint).padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
     Text("Download Videos From\nYour Favorite Platforms", fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
     Spacer(Modifier.height(7.dp)); Text("Videos  •  Reels  •  Shorts  •  More", color = Muted, fontSize = 13.sp)
    }
    Icon(Icons.Default.VideoLibrary, null, tint = DeepMint, modifier = Modifier.size(78.dp))
   }
  }
  item {
   Column(Modifier.padding(horizontal = 16.dp).background(Color.White, RoundedCornerShape(22.dp)).padding(14.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
     OutlinedTextField(value = url, onValueChange = onUrl, modifier = Modifier.weight(1f), placeholder = { Text("Paste video link here...", fontSize = 13.sp) }, leadingIcon = { Icon(Icons.Default.Link, null, tint = DeepMint) }, trailingIcon = { if (url.isNotBlank()) IconButton(onClick = { onUrl("") }) { Icon(Icons.Default.Close, "Clear") } }, shape = RoundedCornerShape(15.dp), singleLine = true)
     Spacer(Modifier.width(8.dp))
     FilledTonalButton(onClick = onPaste, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp)) { Icon(Icons.Default.ContentPaste, null); Spacer(Modifier.width(4.dp)); Text("Paste") }
    }
    Spacer(Modifier.height(12.dp))
    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(30.dp), colors = ButtonDefaults.buttonColors(containerColor = DeepMint)) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(10.dp)); Text("Download", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    if (notice.isNotBlank()) Text(notice, color = DeepMint, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
   }
  }
  item { Text("Supported Platforms", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 18.dp)) }
  item {
   Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    PlatformCard("f", "Facebook", "Videos", Modifier.weight(1f))
    PlatformCard("◎", "Instagram", "Reels & Videos", Modifier.weight(1f))
    PlatformCard("@", "Threads", "Videos", Modifier.weight(1f))
    PlatformCard("▶", "YouTube", "Official options", Modifier.weight(1f))
   }
  }
  item {
   Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FeatureTile("↓", "Batch\nDownload", Modifier.weight(1f))
    FeatureTile("HD", "Choose\nQuality", Modifier.weight(1f))
    FeatureTile("ϟ", "Fast\nDownload", Modifier.weight(1f))
    FeatureTile("▶", "Built-in\nPlayer", Modifier.weight(1f))
   }
  }
  item {
   Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(PaleMint, RoundedCornerShape(20.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
    Text("👑", fontSize = 28.sp); Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) { Text("Media HUB Pro", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp); Text("Ad Free  •  More Features", fontSize = 11.sp, color = Muted) }
    Text("Coming Soon", color = DeepMint, fontWeight = FontWeight.Bold, fontSize = 11.sp)
   }
  }
  item {
   Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(Icons.Default.AccessTime, null); Spacer(Modifier.width(8.dp)); Text("Recent Downloads", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
    Text("View All  ›", color = DeepMint, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onAll() })
   }
  }
  if (downloads.isEmpty()) item { Text("अभी कोई डाउनलोड नहीं है। अपना अधिकृत direct MP4 link ऊपर paste करें।", color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), textAlign = TextAlign.Center) }
  else items(downloads.take(5), key = { it.id }) { DownloadRow(it) }
  item { Text("Social post link direct MP4 file नहीं होता। यह version direct MP4 URLs ही डाउनलोड करता है और DRM/platform restrictions bypass नहीं करता।", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 18.dp)) }
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
@Composable private fun DownloadsPage(downloads: List<DownloadItem>, history: Boolean, modifier: Modifier) {
 Column(modifier.fillMaxSize().padding(18.dp)) {
  Text(if (history) "Download History" else "Your Downloads", fontSize = 24.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp))
  if (downloads.isEmpty()) Text("अभी कोई डाउनलोड उपलब्ध नहीं है।", color = Muted)
  else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(downloads, key = { it.id }) { DownloadRow(it) } }
 }
}
@Composable private fun DownloadRow(item: DownloadItem) {
 Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
  Box(Modifier.size(44.dp).background(PaleMint, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.VideoFile, null, tint = DeepMint) }
  Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1); Text(item.status, color = Muted, fontSize = 11.sp) }
  Icon(Icons.Default.PlayCircle, null, tint = DeepMint)
 }
}
@Composable private fun PlayerPage(downloads: List<DownloadItem>, modifier: Modifier) {
 Column(modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
  Icon(Icons.Default.PlayCircle, null, tint = DeepMint, modifier = Modifier.size(72.dp)); Spacer(Modifier.height(12.dp))
  Text("Media HUB Player", fontSize = 22.sp, fontWeight = FontWeight.Bold)
  Text("यह स्क्रीन डाउनलोड सूची दिखाती है। Playback के लिए completed file को Android के compatible video player में खोलें।", color = Muted, textAlign = TextAlign.Center)
  Spacer(Modifier.height(18.dp)); Text("${downloads.size} download record(s)", color = DeepMint, fontWeight = FontWeight.Bold)
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

private fun startDownload(context: Context, raw: String): String {
 val value = raw.trim()
 if (value.isBlank()) return "पहले video link paste करें।"
 val uri = try { Uri.parse(value) } catch (_: Exception) { return "Link सही नहीं है।" }
 if (uri.scheme != "https" && uri.scheme != "http") return "केवल HTTP/HTTPS link समर्थित है।"
 if (!uri.path.orEmpty().lowercase().endsWith(".mp4")) return "यह direct MP4 link नहीं है। Social post URL extract नहीं किया जाएगा।"
 return try {
  val filename = URLUtil.guessFileName(value, null, "video/mp4")
  val request = DownloadManager.Request(uri).setTitle(filename).setDescription("Media HUB download").setMimeType("video/mp4")
   .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
   .setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "MediaHUB/$filename")
   .setAllowedOverMetered(true).setAllowedOverRoaming(false)
  (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
  "Download queue में जोड़ दिया गया। Notification में progress देखें।"
 } catch (e: Exception) { "Download शुरू नहीं हुआ: ${e.localizedMessage ?: "unknown error"}" }
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
