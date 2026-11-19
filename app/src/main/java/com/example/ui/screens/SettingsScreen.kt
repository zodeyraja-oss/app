package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.TtsPlaybackState
import com.example.data.AppColorTheme
import com.example.data.AuthRepository
import com.example.data.ChatColorTheme
import com.example.data.GeminiTtsVoice
import com.example.data.TextSizePreset
import com.example.data.ThemeMode
import com.example.ui.MainViewModel
import com.example.ui.theme.LocalKinChatColors
import com.example.ui.theme.MonoFontFamily
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val isUpdatingName by viewModel.isUpdatingName.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val extendedColors = LocalKinChatColors.current

    var copiedNotice by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }

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
            Text(
                text = "Profile, Colors & Privacy",
                style = MaterialTheme.typography.headlineMedium
            )

            // Google Account Profile & Editable Name Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile?.displayName ?: "Signed In User",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = userProfile?.email ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { showEditNameDialog = true },
                            enabled = !isUpdatingName,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("edit_name_button")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Change your display name", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Name")
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CloudDone,
                            contentDescription = null,
                            tint = extendedColors.securityGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Profile, colors & preferences saved to Google Account",
                            style = MaterialTheme.typography.labelSmall,
                            color = extendedColors.securityGreen
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Your KinChat Invite Code", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = userProfile?.shareCode ?: "...",
                                fontFamily = MonoFontFamily,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                val code = userProfile?.shareCode ?: return@IconButton
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("KinChat Invite Code", code))
                                copiedNotice = true
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("copy_invite_code_button")
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy invite code")
                        }
                    }
                    if (copiedNotice) {
                        Text(
                            text = "Invite code copied to clipboard.",
                            style = MaterialTheme.typography.labelSmall,
                            color = extendedColors.securityGreen
                        )
                    }
                }
            }

            // App & Chat Colors + Background Mode Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("App & Chat Colors", style = MaterialTheme.typography.titleMedium)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Background Mode", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = "System uses pure black in dark mode and pure white in light mode.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = prefs.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(mode.label) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("theme_mode_${mode.name.lowercase()}")
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("App Accent Color", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AppColorTheme.entries.forEach { colorOption ->
                                val selected = prefs.appColor == colorOption
                                ColorSwatchItem(
                                    color = Color(colorOption.swatchHex),
                                    label = colorOption.label,
                                    selected = selected,
                                    testTag = "app_color_${colorOption.name.lowercase()}",
                                    onClick = { viewModel.setAppColor(colorOption) }
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Chat Bubble Color", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ChatColorTheme.entries.forEach { chatOption ->
                                val swatchColor = if (chatOption == ChatColorTheme.MATCH_APP) {
                                    Color(prefs.appColor.swatchHex)
                                } else {
                                    Color(chatOption.swatchHex)
                                }
                                ColorSwatchItem(
                                    color = swatchColor,
                                    label = chatOption.label,
                                    selected = prefs.chatColor == chatOption,
                                    testTag = "chat_color_${chatOption.name.lowercase()}",
                                    onClick = { viewModel.setChatColor(chatOption) }
                                )
                            }
                        }

                        // Live chat bubble preview
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = extendedColors.incomingBubble,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "Incoming message preview",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Surface(
                                        color = extendedColors.outgoingBubble,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "Your encrypted bubble",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Universal Accessibility & Display Section
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Display & Reading Comfort",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text("Text & Touch Target Size", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextSizePreset.entries.forEach { preset ->
                            FilterChip(
                                selected = prefs.textSizePreset == preset,
                                onClick = { viewModel.setTextSizePreset(preset) },
                                label = { Text(preset.label) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("text_size_${preset.name.lowercase()}")
                            )
                        }
                    }

                    SettingToggleRow(
                        title = "Easy View Navigation",
                        subtitle = "Larger message cards, clear action labels, and simplified spacing",
                        checked = prefs.easyMode,
                        onCheckedChange = { viewModel.setEasyMode(it) },
                        testTag = "settings_easy_mode_switch"
                    )

                    SettingToggleRow(
                        title = "High-Contrast Outlines",
                        subtitle = "Stronger borders around buttons, bubbles, and cards",
                        checked = prefs.highContrast,
                        onCheckedChange = { viewModel.setHighContrast(it) },
                        testTag = "settings_high_contrast_switch"
                    )
                }
            }

            // Gemini Flash TTS Voice Settings
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Read-Aloud Voice (Gemini Flash TTS)", style = MaterialTheme.typography.titleMedium)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GeminiTtsVoice.entries.forEach { voice ->
                            FilterChip(
                                selected = prefs.ttsVoice == voice,
                                onClick = { viewModel.setTtsVoice(voice) },
                                label = { Text("${voice.displayLabel} — ${voice.tone}") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            if (ttsState is TtsPlaybackState.Playing || ttsState is TtsPlaybackState.Synthesizing) {
                                viewModel.stopSpeaking()
                            } else {
                                viewModel.speakMessage(
                                    targetId = "settings_preview",
                                    text = "Hello! KinChat keeps your conversations end-to-end encrypted and easy to read."
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("test_gemini_tts_button")
                    ) {
                        when (ttsState) {
                            is TtsPlaybackState.Synthesizing -> {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Synthesizing with Gemini TTS...")
                            }
                            is TtsPlaybackState.Playing -> {
                                Icon(Icons.Filled.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Stop Voice Preview")
                            }
                            else -> {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Test Selected TTS Voice")
                            }
                        }
                    }
                }
            }

            // E2EE Identity Key Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = extendedColors.securityGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Your E2EE Identity Fingerprint", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = viewModel.localIdentity.keyFingerprint,
                        fontFamily = MonoFontFamily,
                        style = MaterialTheme.typography.bodyMedium,
                        color = extendedColors.securityGreen
                    )
                    Text(
                        text = "Algorithm: EC secp256r1 ECDH + AES-256-GCM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Sign Out
            Button(
                onClick = {
                    scope.launch { AuthRepository.signOut(context) }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
                    contentColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showEditNameDialog) {
        var nameInput by remember(userProfile?.displayName) {
            mutableStateOf(userProfile?.displayName ?: "")
        }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Change Your Name") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Others can find you in KinChat using this name or your Google email.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateDisplayName(nameInput) {
                            showEditNameDialog = false
                        }
                    },
                    enabled = nameInput.trim().isNotEmpty() && !isUpdatingName,
                    modifier = Modifier.testTag("save_name_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ColorSwatchItem(
    color: Color,
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                )
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "$label selected",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
