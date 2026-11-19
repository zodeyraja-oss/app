package com.example.ui.screens

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChatRoom
import com.example.data.UiState
import com.example.ui.MainViewModel
import com.example.ui.theme.LocalKinChatColors
import com.example.util.MediaUtils
import java.io.ByteArrayInputStream

private data class CuratedVideo(
    val id: String,
    val title: String,
    val category: String
)

private val CURATED_VIDEOS = listOf(
    CuratedVideo("aqz-KE-bpKQ", "Big Buck Bunny (Open Film)", "Nature & Film"),
    CuratedVideo("YE7VzlLtp-4", "Big Buck Bunny Trailer", "Short Clip"),
    CuratedVideo("LXb3EKWsInQ", "Costa Rica 4K Nature", "Relaxing"),
    CuratedVideo("5qap5aO4i9A", "Ambient Study & Calm Audio", "Focus")
)

private val BLOCKED_TRACKING_HOSTS = listOf(
    "doubleclick.net",
    "googlesyndication.com",
    "googleadservices.com",
    "google-analytics.com",
    "googletagmanager.com",
    "adservice.google.",
    "/pagead",
    "/api/stats"
)

@Composable
fun PrivacyYouTubeScreen(
    initialVideoId: String,
    initialVideoTitle: String,
    viewModel: MainViewModel,
    onSharedToChat: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    val extendedColors = LocalKinChatColors.current

    var urlInput by remember { mutableStateOf("") }
    var currentVideoId by remember(initialVideoId) { mutableStateOf(initialVideoId.ifBlank { CURATED_VIDEOS.first().id }) }
    var currentTitle by remember(initialVideoTitle) { mutableStateOf(initialVideoTitle.ifBlank { CURATED_VIDEOS.first().title }) }
    var showShareDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Privacy Watch",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = extendedColors.securityGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "No-Cookie Embed • Ad & Tracker Blocking",
                            style = MaterialTheme.typography.labelMedium,
                            color = extendedColors.securityGreen
                        )
                    }
                }
            }

            // Hardened youtube-nocookie.com WebView
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    ) {
                        PrivacyYouTubeWebView(
                            videoId = currentVideoId,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentTitle,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Source: youtube-nocookie.com/embed/$currentVideoId",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = { showShareDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("share_youtube_to_chat_button")
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                }
            }

            // Paste any YouTube link or ID
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Paste YouTube URL or Video ID") },
                    placeholder = { Text("https://youtu.be/...") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("youtube_url_input")
                )
                Button(
                    onClick = {
                        val parsed = MediaUtils.extractYouTubeVideoId(urlInput)
                        if (parsed != null) {
                            currentVideoId = parsed
                            currentTitle = "YouTube Video ($parsed)"
                            urlInput = ""
                        }
                    },
                    enabled = MediaUtils.extractYouTubeVideoId(urlInput) != null,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("load_youtube_button")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Load video")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play")
                }
            }

            Text(
                text = "Quick Picks (Zero Personalized Tracking)",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CURATED_VIDEOS.forEach { video ->
                    FilterChip(
                        selected = currentVideoId == video.id,
                        onClick = {
                            currentVideoId = video.id
                            currentTitle = video.title
                        },
                        label = { Text("${video.title} • ${video.category}") },
                        leadingIcon = {
                            Icon(Icons.Filled.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                }
            }

            // Privacy Guarantee Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = extendedColors.securityGreen
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Cookies, local DOM storage, personalized recommendations (rel=0), and ad/analytics domains are blocked in this player.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showShareDialog) {
        val chats = (chatsState as? UiState.Success)?.data.orEmpty()
        SelectChatToShareDialog(
            title = "Share Ad-Free Video",
            chats = chats,
            onDismiss = { showShareDialog = false },
            onSelectChat = { chat ->
                viewModel.sendYouTubeMessage(chat, currentVideoId, currentTitle)
                showShareDialog = false
                onSharedToChat(chat.id)
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PrivacyYouTubeWebView(
    videoId: String,
    modifier: Modifier = Modifier
) {
    val htmlPayload = remember(videoId) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
          <style>
            html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #000000; overflow: hidden; }
            iframe { width: 100%; height: 100%; border: 0; }
          </style>
        </head>
        <body>
          <iframe
            src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=0&controls=1&modestbranding=1&rel=0&iv_load_policy=3"
            allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture"
            allowfullscreen>
          </iframe>
        </body>
        </html>
        """.trimIndent()
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var useSoftwareLayer by remember { mutableStateOf(false) }
    var rendererUnavailable by remember { mutableStateOf(false) }
    var reloadNonce by remember { mutableIntStateOf(0) }

    DisposableEffect(reloadNonce) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                clearCache(true)
                clearHistory()
                destroy()
            }
            webViewRef = null
        }
    }

    if (rendererUnavailable) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "Video renderer paused by system graphics",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Ad-free embed ready: youtube-nocookie.com/embed/$videoId",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                OutlinedButton(
                    onClick = {
                        useSoftwareLayer = true
                        rendererUnavailable = false
                        reloadNonce++
                    }
                ) {
                    Text("Reload Player")
                }
            }
        }
        return
    }

    key(reloadNonce) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                WebView(context).apply {
                    tag = videoId
                    if (useSoftwareLayer) {
                        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    }
                    CookieManager.getInstance().setAcceptCookie(false)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = false
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        mediaPlaybackRequiresUserGesture = true
                        allowFileAccess = false
                        allowContentAccess = false
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString().orEmpty()
                            // Block escaping to external watch/tracking pages
                            return !url.startsWith("https://www.youtube-nocookie.com/embed/")
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url?.toString()?.lowercase().orEmpty()
                            if (BLOCKED_TRACKING_HOSTS.any { url.contains(it) }) {
                                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: RenderProcessGoneDetail?
                        ): Boolean {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                if (!useSoftwareLayer) {
                                    useSoftwareLayer = true
                                    reloadNonce++
                                } else {
                                    rendererUnavailable = true
                                }
                                return true
                            }
                            return super.onRenderProcessGone(view, detail)
                        }
                    }
                    loadDataWithBaseURL("https://www.youtube-nocookie.com", htmlPayload, "text/html", "utf-8", null)
                    webViewRef = this
                }
            },
            update = { webView ->
                if (webView.tag != videoId) {
                    webView.tag = videoId
                    webView.loadDataWithBaseURL("https://www.youtube-nocookie.com", htmlPayload, "text/html", "utf-8", null)
                }
            }
        )
    }
}

@Composable
fun SelectChatToShareDialog(
    title: String,
    chats: List<ChatRoom>,
    onDismiss: () -> Unit,
    onSelectChat: (ChatRoom) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (chats.isEmpty()) {
                Text("No active chats yet.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chats.forEach { chat ->
                        OutlinedButton(
                            onClick = { onSelectChat(chat) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(chat.title, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
