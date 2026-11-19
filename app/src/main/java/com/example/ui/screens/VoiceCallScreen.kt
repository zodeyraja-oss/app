package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.ActiveCallUiState
import com.example.ui.MainViewModel
import com.example.ui.theme.LocalKinChatColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun VoiceCallScreen(
    callState: ActiveCallUiState,
    chatTitle: String,
    viewModel: MainViewModel,
    onEndCall: () -> Unit
) {
    BackHandler { onEndCall() }

    val micLevel by viewModel.micLevel.collectAsStateWithLifecycle()
    val extendedColors = LocalKinChatColors.current

    val mins = callState.elapsedSeconds / 60
    val secs = callState.elapsedSeconds % 60
    val timerLabel = String.format("%02d:%02d", mins, secs)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .safeDrawingPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "End-to-end encrypted voice call",
                            tint = extendedColors.securityGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "E2EE Verified SAS: ${callState.call.sasCode}",
                            fontFamily = MonoFontFamily,
                            style = MaterialTheme.typography.labelLarge,
                            color = extendedColors.securityGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = chatTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (callState.isMuted) "Microphone Muted • $timerLabel" else "Encrypted Voice Active • $timerLabel",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Live Voice Waveform Ring Driven by AudioRecord Level
            val ringColor = MaterialTheme.colorScheme.primary
            val secondaryRingColor = MaterialTheme.colorScheme.secondary
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(220.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val baseRadius = size.minDimension * 0.28f
                    val pulse = micLevel * size.minDimension * 0.18f
                    drawCircle(
                        color = ringColor.copy(alpha = 0.18f),
                        radius = baseRadius + pulse * 1.4f,
                        center = center
                    )
                    drawCircle(
                        color = secondaryRingColor.copy(alpha = 0.25f),
                        radius = baseRadius + pulse * 0.8f,
                        center = center
                    )
                    drawCircle(
                        color = ringColor.copy(alpha = 0.85f),
                        radius = baseRadius,
                        center = center
                    )
                }
                Icon(
                    imageVector = if (callState.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = "Voice status",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(48.dp)
                )
            }

            // Accessible Large Call Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(
                        onClick = { viewModel.toggleCallMute() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (callState.isMuted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .size(64.dp)
                            .testTag("call_mute_button")
                    ) {
                        Icon(
                            imageVector = if (callState.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = if (callState.isMuted) "Unmute microphone" else "Mute microphone",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(if (callState.isMuted) "Unmute" else "Mute", style = MaterialTheme.typography.labelLarge)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(
                        onClick = onEndCall,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(72.dp)
                            .testTag("end_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CallEnd,
                            contentDescription = "End encrypted voice call",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("End Call", style = MaterialTheme.typography.labelLarge)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(
                        onClick = { viewModel.toggleCallSpeaker() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (callState.isSpeakerOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .size(64.dp)
                            .testTag("call_speaker_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VolumeUp,
                            contentDescription = "Toggle speakerphone",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Speaker", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
