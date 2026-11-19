package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.TtsPlaybackState
import com.example.data.AppColorTheme
import com.example.data.ChatColorTheme
import com.example.data.ChatRoom
import com.example.data.DecryptedMessage
import com.example.data.UiState
import com.example.ui.MainViewModel
import com.example.ui.theme.LocalKinChatColors
import com.example.ui.theme.MonoFontFamily
import com.example.util.MediaUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ConversationScreen(
    chat: ChatRoom,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenMapLocation: (Double, Double, String) -> Unit,
    onOpenYouTubeVideo: (String, String) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val isRecordingNote by viewModel.isRecordingNote.collectAsStateWithLifecycle()
    val playingVoiceId by viewModel.playingVoiceMessageId.collectAsStateWithLifecycle()
    val extendedColors = LocalKinChatColors.current

    var draftText by remember { mutableStateOf("") }
    var showSecurityDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showYouTubeAttachDialog by remember { mutableStateOf(false) }
    var zoomedBitmapBase64 by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val b64 = MediaUtils.uriToCompressedBase64(context, uri)
                if (b64 != null) {
                    viewModel.sendImageMessage(chat, b64, "Encrypted photo")
                }
            }
        }
    }

    var pendingVoiceAction by remember { mutableStateOf<String?>(null) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        when (pendingVoiceAction) {
            "call" -> viewModel.startVoiceCall(chat, granted)
            "note" -> if (granted) viewModel.toggleVoiceNoteRecording(chat)
        }
        pendingVoiceAction = null
    }

    fun launchWithMicPermission(action: String) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            if (action == "call") viewModel.startVoiceCall(chat, true)
            else viewModel.toggleVoiceNoteRecording(chat)
        } else {
            pendingVoiceAction = action
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val messageList = (messagesState as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(messageList.size) {
        if (messageList.isNotEmpty()) {
            listState.animateScrollToItem(messageList.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Conversation Top Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("back_to_chats_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to chats"
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showSecurityDialog = true }
                        .padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = chat.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = extendedColors.securityGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "E2EE • Tap to verify",
                            style = MaterialTheme.typography.labelSmall,
                            color = extendedColors.securityGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Customize App & Chat Colors
                IconButton(
                    onClick = { showColorDialog = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("chat_color_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Palette,
                        contentDescription = "Change app and chat colors",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Read Latest Aloud via Gemini Flash TTS
                val latestText = messageList.lastOrNull()?.toSpeechText()
                IconButton(
                    onClick = {
                        if (ttsState is TtsPlaybackState.Playing || ttsState is TtsPlaybackState.Synthesizing) {
                            viewModel.stopSpeaking()
                        } else if (!latestText.isNullOrBlank()) {
                            viewModel.speakMessage("latest_${chat.id}", latestText)
                        }
                    },
                    enabled = !latestText.isNullOrBlank(),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("read_latest_tts_button")
                ) {
                    Icon(
                        imageVector = if (ttsState is TtsPlaybackState.Playing) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Read latest message aloud with Gemini TTS",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }

                // Secure Voice Call Button
                IconButton(
                    onClick = { launchWithMicPermission("call") },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("start_voice_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "Start encrypted voice call",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // TTS Error Banner if any
        if (ttsState is TtsPlaybackState.Error) {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = (ttsState as TtsPlaybackState.Error).message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.clearTtsError() }) {
                        Text("Dismiss")
                    }
                }
            }
        }

        // Messages Feed
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (val state = messagesState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.VerifiedUser,
                                    contentDescription = null,
                                    tint = extendedColors.securityGreen,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "AES-256-GCM Encrypted Channel",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Send a message, photo, voice note, Google Map pin, or ad-free YouTube video below.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.data, key = { it.id }) { msg ->
                                MessageBubbleCard(
                                    message = msg,
                                    isMine = msg.senderId == viewModel.currentUserId,
                                    ttsState = ttsState,
                                    isPlayingVoice = playingVoiceId == msg.id,
                                    easyMode = prefs.easyMode,
                                    highContrast = prefs.highContrast,
                                    onSpeakToggle = {
                                        if (ttsState is TtsPlaybackState.Playing && (ttsState as TtsPlaybackState.Playing).targetId == msg.id) {
                                            viewModel.stopSpeaking()
                                        } else {
                                            viewModel.speakMessage(msg.id, msg.toSpeechText())
                                        }
                                    },
                                    onVoiceNoteToggle = { viewModel.togglePlayVoiceNote(msg) },
                                    onOpenImage = { msg.imageBase64?.let { zoomedBitmapBase64 = it } },
                                    onOpenLocation = {
                                        if (msg.latitude != null && msg.longitude != null) {
                                            onOpenMapLocation(msg.latitude, msg.longitude, msg.locationLabel ?: "Shared Pin")
                                        }
                                    },
                                    onOpenYouTube = {
                                        if (!msg.youtubeVideoId.isNullOrBlank()) {
                                            onOpenYouTubeVideo(msg.youtubeVideoId, msg.youtubeTitle ?: "YouTube Video")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Expandable Media Attachment Tray
        if (showAttachMenu) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AttachQuickButton(
                        icon = Icons.Filled.Image,
                        label = "Photo",
                        testTag = "attach_photo_button",
                        onClick = {
                            showAttachMenu = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                    AttachQuickButton(
                        icon = Icons.Filled.LocationOn,
                        label = "Map Pin",
                        testTag = "attach_map_button",
                        onClick = {
                            showAttachMenu = false
                            onOpenMapLocation(37.4220, -122.0841, "Pick Location")
                        }
                    )
                    AttachQuickButton(
                        icon = Icons.Filled.PlayCircle,
                        label = "YouTube",
                        testTag = "attach_youtube_button",
                        onClick = {
                            showAttachMenu = false
                            showYouTubeAttachDialog = true
                        }
                    )
                    AttachQuickButton(
                        icon = if (isRecordingNote) Icons.Filled.Stop else Icons.Filled.Mic,
                        label = if (isRecordingNote) "Stop Rec" else "Voice Note",
                        testTag = "attach_voice_note_button",
                        onClick = {
                            showAttachMenu = false
                            launchWithMicPermission("note")
                        }
                    )
                }
            }
        }

        // Composer Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showAttachMenu = !showAttachMenu },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_attachments_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AttachFile,
                        contentDescription = "Share media, location, or video"
                    )
                }

                if (isRecordingNote) {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { launchWithMicPermission("note") }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Recording voice note... Tap to send",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = draftText,
                        onValueChange = { draftText = it },
                        placeholder = { Text("Encrypted message...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("message_composer_input")
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (isRecordingNote) {
                                launchWithMicPermission("note")
                            } else if (draftText.isNotBlank()) {
                                viewModel.sendTextMessage(chat, draftText)
                                draftText = ""
                            } else {
                                launchWithMicPermission("note")
                            }
                        }
                        .testTag("send_message_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (draftText.isNotBlank() || isRecordingNote) {
                                Icons.AutoMirrored.Filled.Send
                            } else {
                                Icons.Filled.Mic
                            },
                            contentDescription = if (draftText.isNotBlank()) "Send encrypted message" else "Record voice note",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }

    if (showSecurityDialog) {
        val fingerprint = remember(chat.id) { viewModel.getRoomFingerprint(chat) }
        val latestCipher = messageList.lastOrNull()?.rawCiphertextPreview ?: "No messages yet"
        AlertDialog(
            onDismissRequest = { showSecurityDialog = false },
            title = { Text("End-to-End Encryption Verified") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Messages, photos, voice notes, and shared locations in this chat are encrypted on your device using AES-256-GCM with keys exchanged via ECDH (P-256).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    HorizontalDivider()
                    Text("Chat Safety Fingerprint:", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = fingerprint,
                        fontFamily = MonoFontFamily,
                        style = MaterialTheme.typography.bodyMedium,
                        color = extendedColors.securityGreen
                    )
                    Text("Latest Cloud Ciphertext Sample:", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "$latestCipher...",
                        fontFamily = MonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showSecurityDialog = false }) { Text("Done") }
            }
        )
    }

    if (showYouTubeAttachDialog) {
        var urlInput by remember { mutableStateOf("") }
        var titleInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showYouTubeAttachDialog = false },
            title = { Text("Share Ad-Free YouTube Video") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Videos open inside KinChat using privacy-enhanced mode (youtube-nocookie.com) with ad & tracker blocking.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("YouTube URL or Video ID") },
                        placeholder = { Text("https://youtu.be/dQw4w9WgXcQ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Title (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                val parsedId = MediaUtils.extractYouTubeVideoId(urlInput)
                Button(
                    onClick = {
                        if (parsedId != null) {
                            viewModel.sendYouTubeMessage(chat, parsedId, titleInput)
                            showYouTubeAttachDialog = false
                        }
                    },
                    enabled = parsedId != null
                ) {
                    Text("Encrypt & Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showYouTubeAttachDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (zoomedBitmapBase64 != null) {
        val bmp = remember(zoomedBitmapBase64) { MediaUtils.base64ToBitmap(zoomedBitmapBase64!!) }
        AlertDialog(
            onDismissRequest = { zoomedBitmapBase64 = null },
            title = { Text("Encrypted Photo") },
            text = {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Zoomed encrypted photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                }
            },
            confirmButton = {
                Button(onClick = { zoomedBitmapBase64 = null }) { Text("Close") }
            }
        )
    }

    if (showColorDialog) {
        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            title = { Text("App & Chat Colors") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("App Accent Color", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppColorTheme.entries.forEach { option ->
                            QuickColorCircle(
                                color = Color(option.swatchHex),
                                label = option.label,
                                selected = prefs.appColor == option,
                                onClick = { viewModel.setAppColor(option) }
                            )
                        }
                    }

                    Text("Chat Bubble Color", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ChatColorTheme.entries.forEach { option ->
                            val color = if (option == ChatColorTheme.MATCH_APP) {
                                Color(prefs.appColor.swatchHex)
                            } else {
                                Color(option.swatchHex)
                            }
                            QuickColorCircle(
                                color = color,
                                label = option.label,
                                selected = prefs.chatColor == option,
                                onClick = { viewModel.setChatColor(option) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showColorDialog = false }) { Text("Done") }
            }
        )
    }
}

@Composable
private fun QuickColorCircle(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                )
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "$label selected",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AttachQuickButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MessageBubbleCard(
    message: DecryptedMessage,
    isMine: Boolean,
    ttsState: TtsPlaybackState,
    isPlayingVoice: Boolean,
    easyMode: Boolean,
    highContrast: Boolean,
    onSpeakToggle: () -> Unit,
    onVoiceNoteToggle: () -> Unit,
    onOpenImage: () -> Unit,
    onOpenLocation: () -> Unit,
    onOpenYouTube: () -> Unit
) {
    val extendedColors = LocalKinChatColors.current
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeText = message.createdAt?.toDate()?.let { timeFormat.format(it) } ?: "Sending"
    val bubbleColor = if (isMine) extendedColors.outgoingBubble else extendedColors.incomingBubble

    val isSynthesizingThis = ttsState is TtsPlaybackState.Synthesizing && ttsState.targetId == message.id
    val isPlayingTtsThis = ttsState is TtsPlaybackState.Playing && ttsState.targetId == message.id

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            border = BorderStroke(
                width = if (highContrast) 1.5.dp else 0.8.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = if (highContrast) 0.9f else 0.4f)
            ),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMine) 18.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 18.dp
            ),
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            Column(modifier = Modifier.padding(if (easyMode) 16.dp else 12.dp)) {
                if (!isMine) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                when (message.messageType) {
                    "image" -> {
                        val bmp = remember(message.imageBase64) {
                            message.imageBase64?.let { MediaUtils.base64ToBitmap(it) }
                        }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Decrypted photo message",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClick = onOpenImage)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        if (message.text.isNotBlank()) {
                            Text(message.text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    "location" -> {
                        OutlinedButton(
                            onClick = onOpenLocation,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = message.locationLabel ?: "Shared Location",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Tap to open on Google Map",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    "youtube" -> {
                        OutlinedButton(
                            onClick = onOpenYouTube,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = message.youtubeTitle ?: "YouTube Video",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Watch ad-free (No Tracking)",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    "voice" -> {
                        Button(
                            onClick = onVoiceNoteToggle,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (isPlayingVoice) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlayingVoice) "Pause voice note" else "Play voice note"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPlayingVoice) "Playing Voice Note..." else "Play Voice Note (${message.voiceDurationSec}s)",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (easyMode) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = extendedColors.securityGreen,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Gemini Flash TTS Read Aloud button (48dp minimum touch target)
                    IconButton(
                        onClick = onSpeakToggle,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("speak_message_${message.id}")
                    ) {
                        if (isSynthesizingThis) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = if (isPlayingTtsThis) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isPlayingTtsThis) "Stop reading aloud" else "Read message aloud with Gemini TTS",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
