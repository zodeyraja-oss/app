package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.UiState
import com.example.ui.MainViewModel
import com.example.ui.theme.LocalKinChatColors
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

private const val DARK_MAP_STYLE_JSON = """
[
  {"elementType":"geometry","stylers":[{"color":"#121d22"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#b5c7c1"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#0b1215"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#21353d"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#09161b"}]},
  {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#0f2a24"}]}
]
"""

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    initialLat: Double,
    initialLng: Double,
    initialLabel: String,
    viewModel: MainViewModel,
    onSharedToChat: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    val extendedColors = LocalKinChatColors.current

    var selectedLatLng by remember(initialLat, initialLng) {
        mutableStateOf(LatLng(initialLat, initialLng))
    }
    var pinLabel by remember(initialLabel) {
        mutableStateOf(initialLabel.ifBlank { "Shared Meeting Spot" })
    }
    var showShareDialog by remember { mutableStateOf(false) }
    var locationNotice by remember { mutableStateOf<String?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(selectedLatLng, 13f)
    }
    val markerState = remember { MarkerState(position = selectedLatLng) }

    LaunchedEffect(selectedLatLng) {
        markerState.position = selectedLatLng
    }

    LaunchedEffect(initialLat, initialLng) {
        val target = LatLng(initialLat, initialLng)
        selectedLatLng = target
        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target, 14f))
    }

    fun fetchCurrentCoarseLocation() {
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    val latLng = LatLng(loc.latitude, loc.longitude)
                    selectedLatLng = latLng
                    pinLabel = "My Current Area"
                    locationNotice = null
                } else {
                    locationNotice = "Location unavailable right now. Tap anywhere on the map to place a pin."
                }
            }
            .addOnFailureListener {
                locationNotice = "Could not read device location. Tap on the map to choose a pin."
            }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            fetchCurrentCoarseLocation()
        } else {
            locationNotice = "Location permission denied. You can still tap anywhere on the map to pick a pin."
        }
    }

    val rawMapsKey = BuildConfig.MAPS_API_KEY.trim()
    val hasValidMapsKey = rawMapsKey.startsWith("AIza") && rawMapsKey.length >= 30

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Encrypted Map",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = extendedColors.securityGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tap map to drop pin • Shared E2EE only",
                            style = MaterialTheme.typography.labelMedium,
                            color = extendedColors.securityGreen
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) fetchCurrentCoarseLocation()
                        else locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("use_my_location_button")
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "Center on my location", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Locate")
                }
            }

            if (!hasValidMapsKey) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val keyNote = if (rawMapsKey.isNotBlank() && rawMapsKey != "YOUR_GOOGLE_MAPS_API_KEY") {
                        "Current MAPS_API_KEY ('$rawMapsKey') is not a Google Maps API key. "
                    } else {
                        ""
                    }
                    Text(
                        text = "${keyNote}Set a valid Google Maps Android API v2 key (AIza...) in the AI Studio Secrets panel for live map tiles. Tap the grid below to pick & send an encrypted pin right now.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (!locationNotice.isNullOrBlank()) {
                Text(
                    text = locationNotice ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            // Interactive Google Map (or Encrypted Coordinate Pin Grid when Maps key is not configured)
            Surface(
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (hasValidMapsKey) {
                    val mapStyle = remember(extendedColors.isDark) {
                        if (extendedColors.isDark) MapStyleOptions(DARK_MAP_STYLE_JSON) else null
                    }
                    GoogleMap(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp)),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(
                            mapStyleOptions = mapStyle
                        ),
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = true,
                            myLocationButtonEnabled = false
                        ),
                        onMapClick = { latLng ->
                            selectedLatLng = latLng
                        }
                    ) {
                        Marker(
                            state = markerState,
                            title = pinLabel,
                            snippet = "${String.format("%.4f", selectedLatLng.latitude)}, ${String.format("%.4f", selectedLatLng.longitude)}"
                        )
                    }
                } else {
                    EncryptedCoordinatePinPad(
                        initialLat = initialLat,
                        initialLng = initialLng,
                        selectedLatLng = selectedLatLng,
                        pinLabel = pinLabel,
                        onSelectLatLng = { selectedLatLng = it }
                    )
                }
            }

            OutlinedTextField(
                value = pinLabel,
                onValueChange = { pinLabel = it },
                label = { Text("Location Label") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("map_pin_label_input")
            )

            Button(
                onClick = { showShareDialog = true },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("share_map_pin_button")
            ) {
                Icon(Icons.Filled.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Send Encrypted Pin (${String.format("%.3f", selectedLatLng.latitude)}, ${String.format("%.3f", selectedLatLng.longitude)})",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }

    if (showShareDialog) {
        val chats = (chatsState as? UiState.Success)?.data.orEmpty()
        SelectChatToShareDialog(
            title = "Share Encrypted Location Pin",
            chats = chats,
            onDismiss = { showShareDialog = false },
            onSelectChat = { chat ->
                viewModel.sendLocationMessage(
                    chat = chat,
                    latitude = selectedLatLng.latitude,
                    longitude = selectedLatLng.longitude,
                    label = pinLabel
                )
                showShareDialog = false
                onSharedToChat(chat.id)
            }
        )
    }
}

@Composable
private fun EncryptedCoordinatePinPad(
    initialLat: Double,
    initialLng: Double,
    selectedLatLng: LatLng,
    pinLabel: String,
    onSelectLatLng: (LatLng) -> Unit
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val accentColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(initialLat, initialLng) {
                detectTapGestures { offset ->
                    val normX = (offset.x / size.width.coerceAtLeast(1)) - 0.5f
                    val normY = 0.5f - (offset.y / size.height.coerceAtLeast(1))
                    val nextLat = (initialLat + normY * 0.08).coerceIn(-85.0, 85.0)
                    val nextLng = (initialLng + normX * 0.08).coerceIn(-180.0, 180.0)
                    onSelectLatLng(LatLng(nextLat, nextLng))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cols = 8
            val rows = 8
            for (i in 1 until cols) {
                val x = size.width * (i / cols.toFloat())
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            }
            for (j in 1 until rows) {
                val y = size.height * (j / rows.toFloat())
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            val relX = (((selectedLatLng.longitude - initialLng) / 0.08) + 0.5).coerceIn(0.08, 0.92).toFloat()
            val relY = (0.5 - ((selectedLatLng.latitude - initialLat) / 0.08)).coerceIn(0.08, 0.92).toFloat()
            val pinCenter = Offset(size.width * relX, size.height * relY)
            drawCircle(color = accentColor.copy(alpha = 0.2f), radius = 34.dp.toPx(), center = pinCenter)
            drawCircle(color = secondaryColor, radius = 9.dp.toPx(), center = pinCenter)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = pinLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Tap grid to move pin • ${String.format("%.4f", selectedLatLng.latitude)}, ${String.format("%.4f", selectedLatLng.longitude)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
