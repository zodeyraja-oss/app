package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AuthRepository
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Button press shrink -> bounce back to normal scale animation
    val buttonScale = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        val autoSignedIn = AuthRepository.attemptSilentAutoSignIn(context)
        if (autoSignedIn) {
            onAuthSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 540.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_welcome_shield),
                        contentDescription = "KinChat encrypted messaging illustration",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(24.dp))
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "KinChat",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Private, ad-free messaging, voice calls, maps, and speech built for effortless everyday clarity.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                FeatureHighlightCard(
                    icon = Icons.Filled.Lock,
                    title = "End-to-End Encrypted",
                    subtitle = "ECDH + AES-256-GCM locks messages, photos, voice notes, and locations on your device."
                )
                Spacer(modifier = Modifier.height(12.dp))
                FeatureHighlightCard(
                    icon = Icons.Filled.PlayCircle,
                    title = "Maps & Ad-Free YouTube",
                    subtitle = "Share location pins and watch videos via privacy-enhanced playback without ad tracking."
                )
                Spacer(modifier = Modifier.height(12.dp))
                FeatureHighlightCard(
                    icon = Icons.Filled.AccessibilityNew,
                    title = "Google Sync & Universal Comfort",
                    subtitle = "Save your name, app colors, chat theme, and accessibility settings directly on your Google account."
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!errorMessage.isNullOrBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        if (isLoading) return@Button
                        errorMessage = null
                        scope.launch {
                            // Shrink smaller then return to normal size
                            buttonScale.animateTo(
                                targetValue = 0.84f,
                                animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing)
                            )
                            buttonScale.animateTo(
                                targetValue = 1.0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                            isLoading = true
                            when (val outcome = AuthRepository.signInWithGoogleInteractive(context)) {
                                is AuthRepository.SignInOutcome.Success -> {
                                    isLoading = false
                                    onAuthSuccess()
                                }
                                is AuthRepository.SignInOutcome.Cancelled -> {
                                    isLoading = false
                                }
                                is AuthRepository.SignInOutcome.Error -> {
                                    isLoading = false
                                    errorMessage = outcome.message
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .graphicsLayer {
                            scaleX = buttonScale.value
                            scaleY = buttonScale.value
                        }
                        .testTag("google_sign_in_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Signing in securely...", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                GoogleGLogo(modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Sign in with Google",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Vector-drawn 4-color Google "G" mark used on the Sign-In button and center transition animation.
 */
@Composable
fun GoogleGLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val diameter = size.minDimension
        val strokeWidth = diameter * 0.21f
        val inset = strokeWidth / 2f
        val arcSize = Size(diameter - strokeWidth, diameter - strokeWidth)
        val topLeft = Offset((size.width - arcSize.width) / 2f, (size.height - arcSize.height) / 2f)

        val googleBlue = Color(0xFF4285F4)
        val googleRed = Color(0xFFEA4335)
        val googleYellow = Color(0xFFFBBC05)
        val googleGreen = Color(0xFF34A853)

        // Red top arc (-148 deg to -40 deg)
        drawArc(
            color = googleRed,
            startAngle = -148f,
            sweepAngle = 108f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Yellow left arc (152 deg to 212 deg)
        drawArc(
            color = googleYellow,
            startAngle = 150f,
            sweepAngle = 64f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Green bottom arc (42 deg to 152 deg)
        drawArc(
            color = googleGreen,
            startAngle = 42f,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Blue right-bottom arc (-8 deg to 45 deg)
        drawArc(
            color = googleBlue,
            startAngle = -8f,
            sweepAngle = 54f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Blue horizontal crossbar
        val centerY = size.height / 2f
        drawLine(
            color = googleBlue,
            start = Offset(size.width / 2f, centerY),
            end = Offset(topLeft.x + arcSize.width + inset * 0.35f, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Butt
        )
    }
}

@Composable
private fun FeatureHighlightCard(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
