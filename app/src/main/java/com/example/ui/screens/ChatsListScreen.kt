package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ChatRoom
import com.example.data.Contact
import com.example.data.DirectoryEntry
import com.example.data.TextSizePreset
import com.example.data.UiState
import com.example.data.VoiceCallDoc
import com.example.ui.MainViewModel
import com.example.ui.theme.LocalKinChatColors
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ChatsListScreen(
    viewModel: MainViewModel,
    onOpenChat: (String) -> Unit,
    onJoinCall: (VoiceCallDoc) -> Unit,
    modifier: Modifier = Modifier
) {
    val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    val contactsState by viewModel.contactsState.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val activeCalls by viewModel.remoteActiveCalls.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val peerSearchState by viewModel.peerSearchState.collectAsStateWithLifecycle()
    val extendedColors = LocalKinChatColors.current

    var searchQuery by remember { mutableStateOf("") }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var showFindPeopleDialog by remember { mutableStateOf(false) }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
        ) {
            // Top Brand + E2EE Status Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "KinChat",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "End-to-end encrypted",
                            tint = extendedColors.securityGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${userProfile?.displayName ?: "You"} • Code: ${userProfile?.shareCode ?: "..."}",
                            style = MaterialTheme.typography.labelMedium,
                            color = extendedColors.securityGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.clearPeerSearch()
                        showFindPeopleDialog = true
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("find_people_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PersonSearch,
                        contentDescription = "Find people by email, name, or code",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Find People")
                }
            }

            // Accessibility Quick Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = prefs.easyMode,
                    onClick = { viewModel.setEasyMode(!prefs.easyMode) },
                    label = { Text(if (prefs.easyMode) "Easy View: ON" else "Easy View") },
                    leadingIcon = {
                        Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.testTag("toggle_easy_mode_chip")
                )
                FilterChip(
                    selected = prefs.textSizePreset != TextSizePreset.COMFORT,
                    onClick = {
                        val next = when (prefs.textSizePreset) {
                            TextSizePreset.COMFORT -> TextSizePreset.LARGE
                            TextSizePreset.LARGE -> TextSizePreset.EXTRA_LARGE
                            TextSizePreset.EXTRA_LARGE -> TextSizePreset.COMFORT
                        }
                        viewModel.setTextSizePreset(next)
                    },
                    label = { Text("Text: ${prefs.textSizePreset.label}") },
                    leadingIcon = {
                        Icon(Icons.Filled.FormatSize, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.testTag("cycle_text_size_chip")
                )
                FilterChip(
                    selected = prefs.highContrast,
                    onClick = { viewModel.setHighContrast(!prefs.highContrast) },
                    label = { Text(if (prefs.highContrast) "High Contrast" else "Contrast") },
                    leadingIcon = {
                        Icon(Icons.Filled.Contrast, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.testTag("toggle_contrast_chip")
                )
            }

            // Active Voice Call Banner if any
            if (activeCalls.isNotEmpty()) {
                val call = activeCalls.first()
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clickable { onJoinCall(call) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = "Active encrypted voice call",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ongoing Encrypted Voice Call",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${call.callerName} • SAS ${call.sasCode} • Tap to return",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Search Chats & Saved Contacts Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search chats or contacts by name...") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = "Search chats and contacts")
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag("search_chats_input")
            )

            // Matching Saved Contacts strip when searching
            val contacts = (contactsState as? UiState.Success)?.data.orEmpty()
            val matchingContacts = remember(contacts, searchQuery) {
                if (searchQuery.isBlank()) emptyList()
                else contacts.filter {
                    it.displayName.contains(searchQuery.trim(), ignoreCase = true) ||
                        it.shareCode.contains(searchQuery.trim(), ignoreCase = true)
                }
            }
            if (matchingContacts.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(
                        text = "Matching Contacts (tap to chat)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        matchingContacts.forEach { contact ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    viewModel.startDirectChatWithContact(contact) { roomId ->
                                        onOpenChat(roomId)
                                    }
                                },
                                label = { Text(contact.displayName) }
                            )
                        }
                    }
                }
            }

            // Chats List Content
            when (val state = chatsState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is UiState.Success -> {
                    val filtered = remember(state.data, searchQuery) {
                        if (searchQuery.isBlank()) state.data
                        else state.data.filter { it.title.contains(searchQuery.trim(), ignoreCase = true) }
                    }

                    if (filtered.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_empty_chats),
                                contentDescription = "No conversations yet",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(RoundedCornerShape(24.dp))
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Start an Encrypted Conversation",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap Find People to search by email, name, or invite code and start chatting.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(if (prefs.easyMode) 14.dp else 10.dp)
                        ) {
                            items(filtered, key = { it.id }) { chat ->
                                ChatListItemCard(
                                    chat = chat,
                                    preview = viewModel.getDecryptedPreview(chat),
                                    timeLabel = chat.updatedAt?.toDate()?.let { timeFormat.format(it) } ?: "Now",
                                    easyMode = prefs.easyMode,
                                    highContrast = prefs.highContrast,
                                    onClick = { onOpenChat(chat.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showNewChatDialog = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = "Create encrypted chat") },
            text = { Text("New Encrypted Chat") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("new_chat_fab")
        )
    }

    if (showFindPeopleDialog) {
        FindPeopleDialog(
            myShareCode = userProfile?.shareCode ?: "",
            myDisplayName = userProfile?.displayName ?: "",
            searchState = peerSearchState,
            onDismiss = {
                viewModel.clearPeerSearch()
                showFindPeopleDialog = false
            },
            onSearch = { query -> viewModel.searchPeers(query) },
            onAddAndChat = { entry ->
                viewModel.addPeerFromDirectory(entry) { contact ->
                    showFindPeopleDialog = false
                    viewModel.clearPeerSearch()
                    viewModel.startDirectChatWithContact(contact) { roomId ->
                        onOpenChat(roomId)
                    }
                }
            },
            onAddOnly = { entry ->
                viewModel.addPeerFromDirectory(entry) {
                    showFindPeopleDialog = false
                    viewModel.clearPeerSearch()
                }
            }
        )
    }

    if (showNewChatDialog) {
        val contacts = (contactsState as? UiState.Success)?.data.orEmpty()
        NewChatDialog(
            contacts = contacts,
            onDismiss = { showNewChatDialog = false },
            onCreate = { title, selectedContacts ->
                viewModel.createChatRoom(title, selectedContacts) { roomId ->
                    showNewChatDialog = false
                    onOpenChat(roomId)
                }
            }
        )
    }
}

@Composable
private fun ChatListItemCard(
    chat: ChatRoom,
    preview: String,
    timeLabel: String,
    easyMode: Boolean,
    highContrast: Boolean,
    onClick: () -> Unit
) {
    val extendedColors = LocalKinChatColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(
            width = if (highContrast) 1.8.dp else 1.dp,
            color = if (highContrast) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .testTag("chat_item_${chat.id}")
    ) {
        Row(
            modifier = Modifier.padding(if (easyMode) 18.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(if (easyMode) 56.dp else 48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (chat.chatType) {
                            "vault" -> Icons.Filled.Shield
                            "group" -> Icons.Filled.Group
                            else -> Icons.Filled.Lock
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chat.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = extendedColors.securityGreen,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (easyMode) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun FindPeopleDialog(
    myShareCode: String,
    myDisplayName: String,
    searchState: UiState<List<DirectoryEntry>>?,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onAddAndChat: (DirectoryEntry) -> Unit,
    onAddOnly: (DirectoryEntry) -> Unit
) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Find People on KinChat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Search by someone's email address, full name, or KinChat code.\nYour name: $myDisplayName • Code: $myShareCode",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Email, Name, or Code") },
                        placeholder = { Text("alex@gmail.com or Alex") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("find_people_input")
                    )
                    Button(
                        onClick = { onSearch(query) },
                        enabled = query.trim().length >= 2,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("search_people_button")
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                }

                when (searchState) {
                    null -> {}
                    is UiState.Loading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                        }
                    }
                    is UiState.Error -> {
                        Text(
                            text = searchState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is UiState.Success -> {
                        if (searchState.data.isEmpty()) {
                            Text(
                                text = "No user found matching \"${query.trim()}\". Make sure they have signed into KinChat, or verify their exact email, name, or code.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            searchState.data.forEach { entry ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = entry.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Code: ${entry.shareCode} • Key: ${entry.keyFingerprint.take(14)}...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { onAddOnly(entry) },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Save")
                                            }
                                            Button(
                                                onClick = { onAddAndChat(entry) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("add_and_chat_${entry.userId}")
                                            ) {
                                                Text("Chat")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun NewChatDialog(
    contacts: List<Contact>,
    onDismiss: () -> Unit,
    onCreate: (String, List<Contact>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<Contact>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Encrypted Room") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Conversation Name") },
                    placeholder = { Text("e.g. Family Circle") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_chat_title_input")
                )
                if (contacts.isEmpty()) {
                    Text(
                        text = "No saved contacts yet — tap Find People to search by email, name, or code first, or create a private room now.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("Select participants:", style = MaterialTheme.typography.labelLarge)
                    contacts.forEach { contact ->
                        val isChecked = selected.any { it.contactUid == contact.contactUid }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) selected.removeAll { it.contactUid == contact.contactUid }
                                    else selected.add(contact)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) selected.add(contact)
                                    else selected.removeAll { it.contactUid == contact.contactUid }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(contact.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Key: ${contact.keyFingerprint.take(14)}...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title.ifBlank { "Private Room" }, selected.toList()) },
                modifier = Modifier.testTag("confirm_create_chat_button")
            ) {
                Text("Create E2EE Chat")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
