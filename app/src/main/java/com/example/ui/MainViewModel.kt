package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiTtsService
import com.example.ai.TtsPlaybackState
import com.example.audio.VoiceCallAudioEngine
import com.example.crypto.IdentityKeys
import com.example.data.AccessibilityPreferences
import com.example.data.AppColorTheme
import com.example.data.ChatColorTheme
import com.example.data.ChatRepository
import com.example.data.ChatRoom
import com.example.data.Contact
import com.example.data.DecryptedMessage
import com.example.data.DirectoryEntry
import com.example.data.GeminiTtsVoice
import com.example.data.PreferencesRepository
import com.example.data.TextSizePreset
import com.example.data.ThemeMode
import com.example.data.UiState
import com.example.data.UserPrivate
import com.example.data.VoiceCallDoc
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ActiveCallUiState(
    val call: VoiceCallDoc,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val elapsedSeconds: Long = 0L
)

class MainViewModel(
    private val chatRepo: ChatRepository,
    private val prefsRepo: PreferencesRepository,
    private val ttsService: GeminiTtsService,
    val audioEngine: VoiceCallAudioEngine,
    val currentUserId: String
) : ViewModel() {

    val preferences: StateFlow<AccessibilityPreferences> = prefsRepo.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AccessibilityPreferences()
        )

    val ttsState: StateFlow<TtsPlaybackState> = ttsService.state
    val micLevel: StateFlow<Float> = audioEngine.micLevel
    val isRecordingNote: StateFlow<Boolean> = audioEngine.isRecordingNote
    val playingVoiceMessageId: StateFlow<String?> = audioEngine.playingVoiceMessageId

    private val _userProfile = MutableStateFlow<UserPrivate?>(null)
    val userProfile: StateFlow<UserPrivate?> = _userProfile.asStateFlow()

    private val _isUpdatingName = MutableStateFlow(false)
    val isUpdatingName: StateFlow<Boolean> = _isUpdatingName.asStateFlow()

    private val _peerSearchState = MutableStateFlow<UiState<List<DirectoryEntry>>?>(null)
    val peerSearchState: StateFlow<UiState<List<DirectoryEntry>>?> = _peerSearchState.asStateFlow()

    val localIdentity: IdentityKeys = chatRepo.getLocalIdentity(currentUserId)

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    private val _activeCallUi = MutableStateFlow<ActiveCallUiState?>(null)
    val activeCallUi: StateFlow<ActiveCallUiState?> = _activeCallUi.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var callTimerJob: Job? = null
    private var micMeterJob: Job? = null
    private var ttsJob: Job? = null
    private var voiceNoteRecordJob: Job? = null

    val chatsState: StateFlow<UiState<List<ChatRoom>>> = chatRepo.observeChats(currentUserId)
        .map<List<ChatRoom>, UiState<List<ChatRoom>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing chats", rootError)
            emit(UiState.Error(rootError.message ?: "Unable to load chats"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val contactsState: StateFlow<UiState<List<Contact>>> = chatRepo.observeContacts(currentUserId)
        .map<List<Contact>, UiState<List<Contact>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing contacts", rootError)
            emit(UiState.Error(rootError.message ?: "Unable to load contacts"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val remoteActiveCalls: StateFlow<List<VoiceCallDoc>> = chatRepo.observeActiveCalls(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val messagesState: StateFlow<UiState<List<DecryptedMessage>>> = combine(
        selectedChatId,
        chatsState
    ) { chatId, chats ->
        val chatList = (chats as? UiState.Success)?.data.orEmpty()
        chatId to chatList.firstOrNull { it.id == chatId }
    }.flatMapLatest { (chatId, chatRoom) ->
        if (chatId.isNullOrBlank() || chatRoom == null) {
            flowOf(UiState.Success(emptyList()))
        } else {
            chatRepo.observeMessages(currentUserId, chatId)
                .map<List<com.example.data.EncryptedMessageDoc>, UiState<List<DecryptedMessage>>> { docs ->
                    val decrypted = docs.map { doc ->
                        chatRepo.decryptMessage(currentUserId, chatRoom, doc)
                    }
                    UiState.Success(decrypted)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing messages", rootError)
                    emit(UiState.Error(rootError.message ?: "Unable to load messages"))
                }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = UiState.Loading
    )

    init {
        viewModelScope.launch {
            chatRepo.ensureUserProvisionedAndVault()
                .onSuccess { profile ->
                    _userProfile.value = profile
                    prefsRepo.applyCloudProfilePreferences(profile)
                }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Profile sync issue" }
        }
    }

    fun selectChat(chatId: String?) {
        _selectedChatId.value = chatId
    }

    fun getDecryptedPreview(chat: ChatRoom): String {
        return chatRepo.decryptChatPreview(currentUserId, chat)
    }

    fun getRoomFingerprint(chat: ChatRoom): String {
        val key = chatRepo.resolveRoomKey(currentUserId, chat) ?: return localIdentity.keyFingerprint
        val md = java.security.MessageDigest.getInstance("SHA-256").digest(key)
        return md.take(12).joinToString("") { "%02X".format(it) }.chunked(4).joinToString(" ")
    }

    fun updateDisplayName(newName: String, onSuccess: () -> Unit = {}) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            _statusMessage.value = "Name cannot be empty."
            return
        }
        viewModelScope.launch {
            _isUpdatingName.value = true
            chatRepo.updateDisplayName(trimmed, _userProfile.value?.displayName)
                .onSuccess { updated ->
                    _userProfile.value = updated
                    _statusMessage.value = "Name updated to ${updated.displayName} and saved to your Google account."
                    onSuccess()
                }
                .onFailure {
                    _statusMessage.value = it.localizedMessage ?: "Failed to update name."
                }
            _isUpdatingName.value = false
        }
    }

    fun searchPeers(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _peerSearchState.value = UiState.Error("Type a full email address, full name, or invite code.")
            return
        }
        viewModelScope.launch {
            _peerSearchState.value = UiState.Loading
            chatRepo.searchPeers(trimmed)
                .onSuccess { results ->
                    _peerSearchState.value = UiState.Success(results)
                }
                .onFailure { err ->
                    _peerSearchState.value = UiState.Error(err.localizedMessage ?: "Search failed.")
                }
        }
    }

    fun clearPeerSearch() {
        _peerSearchState.value = null
    }

    fun addPeerFromDirectory(entry: DirectoryEntry, onSuccess: (Contact) -> Unit = {}) {
        viewModelScope.launch {
            chatRepo.addContactFromDirectory(entry)
                .onSuccess { contact ->
                    _statusMessage.value = "Added ${contact.displayName} to encrypted contacts."
                    onSuccess(contact)
                }
                .onFailure {
                    _statusMessage.value = it.localizedMessage ?: "Could not add contact."
                }
        }
    }

    fun startDirectChatWithContact(contact: Contact, onOpened: (String) -> Unit) {
        val existingChats = (chatsState.value as? UiState.Success)?.data.orEmpty()
        val existing = existingChats.firstOrNull { chat ->
            chat.chatType == "direct" &&
                chat.participantUids.contains(contact.contactUid) &&
                chat.participantUids.size == 2
        }
        if (existing != null) {
            _selectedChatId.value = existing.id
            onOpened(existing.id)
            return
        }
        createChatRoom(contact.displayName, listOf(contact), onOpened)
    }

    fun createChatRoom(title: String, contacts: List<Contact>, onCreated: (String) -> Unit) {
        val chatType = when {
            contacts.isEmpty() -> "vault"
            contacts.size == 1 -> "direct"
            else -> "group"
        }
        viewModelScope.launch {
            chatRepo.createChatRoom(title, chatType, contacts)
                .onSuccess { roomId ->
                    _selectedChatId.value = roomId
                    onCreated(roomId)
                }
                .onFailure {
                    _statusMessage.value = it.localizedMessage ?: "Failed to create chat."
                }
        }
    }

    fun sendTextMessage(chat: ChatRoom, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val payload = DecryptedMessage.encodePayloadJson(text = trimmed)
            chatRepo.sendEncryptedMessage(chat, "text", payload, _userProfile.value?.displayName)
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to send message." }
        }
    }

    fun sendImageMessage(chat: ChatRoom, base64Image: String, caption: String) {
        viewModelScope.launch {
            val payload = DecryptedMessage.encodePayloadJson(
                text = caption.trim(),
                imageBase64 = base64Image
            )
            chatRepo.sendEncryptedMessage(chat, "image", payload, _userProfile.value?.displayName)
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to send photo." }
        }
    }

    fun sendLocationMessage(chat: ChatRoom, latitude: Double, longitude: Double, label: String) {
        viewModelScope.launch {
            val cleanLabel = label.trim().ifBlank { "Shared Map Pin" }
            val payload = DecryptedMessage.encodePayloadJson(
                text = "$cleanLabel (${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)})",
                latitude = latitude,
                longitude = longitude,
                locationLabel = cleanLabel
            )
            chatRepo.sendEncryptedMessage(chat, "location", payload, _userProfile.value?.displayName)
                .onSuccess { _statusMessage.value = "Encrypted location sent to ${chat.title}." }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to send location." }
        }
    }

    fun sendYouTubeMessage(chat: ChatRoom, videoId: String, videoTitle: String) {
        viewModelScope.launch {
            val cleanTitle = videoTitle.trim().ifBlank { "YouTube Video ($videoId)" }
            val payload = DecryptedMessage.encodePayloadJson(
                text = cleanTitle,
                youtubeVideoId = videoId,
                youtubeTitle = cleanTitle
            )
            chatRepo.sendEncryptedMessage(chat, "youtube", payload, _userProfile.value?.displayName)
                .onSuccess { _statusMessage.value = "Ad-free video link sent to ${chat.title}." }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to send video." }
        }
    }

    fun toggleVoiceNoteRecording(chat: ChatRoom) {
        if (audioEngine.isRecordingNote.value) {
            audioEngine.stopVoiceNoteRecording()
            return
        }
        voiceNoteRecordJob?.cancel()
        voiceNoteRecordJob = viewModelScope.launch {
            val recorded = audioEngine.recordVoiceNote()
            if (recorded != null) {
                val (b64, duration) = recorded
                val payload = DecryptedMessage.encodePayloadJson(
                    text = "Voice note (${duration}s)",
                    voiceAudioBase64 = b64,
                    voiceDurationSec = duration
                )
                chatRepo.sendEncryptedMessage(chat, "voice", payload, _userProfile.value?.displayName)
                    .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to send voice note." }
            }
        }
    }

    fun togglePlayVoiceNote(message: DecryptedMessage) {
        val audio = message.voiceAudioBase64 ?: return
        if (audioEngine.playingVoiceMessageId.value == message.id) {
            audioEngine.stopVoiceNotePlayback()
        } else {
            viewModelScope.launch {
                audioEngine.playVoiceNote(message.id, audio)
            }
        }
    }

    fun speakMessage(targetId: String, text: String) {
        ttsJob?.cancel()
        ttsJob = viewModelScope.launch {
            val voice = preferences.value.ttsVoice
            ttsService.speak(targetId, text, voice)
        }
    }

    fun stopSpeaking() {
        ttsJob?.cancel()
        ttsService.stop()
    }

    fun clearTtsError() {
        ttsService.clearError()
    }

    fun startVoiceCall(chat: ChatRoom, hasMicPermission: Boolean) {
        viewModelScope.launch {
            chatRepo.startVoiceCall(chat, _userProfile.value?.displayName)
                .onSuccess { call ->
                    _activeCallUi.value = ActiveCallUiState(call = call)
                    audioEngine.setMuted(false)
                    audioEngine.setSpeakerphone(false)
                    startCallTimer()
                    if (hasMicPermission) {
                        micMeterJob?.cancel()
                        micMeterJob = viewModelScope.launch {
                            audioEngine.startCallMicMetering()
                        }
                    }
                }
                .onFailure {
                    _statusMessage.value = it.localizedMessage ?: "Unable to start encrypted call."
                }
        }
    }

    fun joinExistingCall(call: VoiceCallDoc, hasMicPermission: Boolean) {
        _activeCallUi.value = ActiveCallUiState(call = call, elapsedSeconds = call.durationSeconds)
        audioEngine.setMuted(false)
        startCallTimer()
        if (hasMicPermission) {
            micMeterJob?.cancel()
            micMeterJob = viewModelScope.launch {
                audioEngine.startCallMicMetering()
            }
        }
    }

    private fun startCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _activeCallUi.value = _activeCallUi.value?.let {
                    it.copy(elapsedSeconds = it.elapsedSeconds + 1)
                }
            }
        }
    }

    fun toggleCallMute() {
        _activeCallUi.value = _activeCallUi.value?.let {
            val next = !it.isMuted
            audioEngine.setMuted(next)
            it.copy(isMuted = next)
        }
    }

    fun toggleCallSpeaker() {
        _activeCallUi.value = _activeCallUi.value?.let {
            val next = !it.isSpeakerOn
            audioEngine.setSpeakerphone(next)
            it.copy(isSpeakerOn = next)
        }
    }

    fun endActiveCall(chatRoom: ChatRoom?) {
        val current = _activeCallUi.value ?: return
        callTimerJob?.cancel()
        micMeterJob?.cancel()
        audioEngine.stopCallMicMetering()
        _activeCallUi.value = null

        viewModelScope.launch {
            chatRepo.endVoiceCall(current.call.id, current.elapsedSeconds, "ended")
            if (chatRoom != null) {
                val mins = current.elapsedSeconds / 60
                val secs = current.elapsedSeconds % 60
                val label = "Encrypted voice call ended (${String.format("%02d:%02d", mins, secs)} • SAS ${current.call.sasCode})"
                val payload = DecryptedMessage.encodePayloadJson(text = label)
                chatRepo.sendEncryptedMessage(chatRoom, "call", payload, _userProfile.value?.displayName)
            }
        }
    }

    private fun syncPrefsToCloud(updated: AccessibilityPreferences) {
        viewModelScope.launch {
            chatRepo.syncPreferencesToAccount(updated)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        val updated = preferences.value.copy(themeMode = mode)
        viewModelScope.launch {
            prefsRepo.setThemeMode(mode)
            syncPrefsToCloud(updated)
        }
    }

    fun setAppColor(color: AppColorTheme) {
        val updated = preferences.value.copy(appColor = color)
        viewModelScope.launch {
            prefsRepo.setAppColor(color)
            syncPrefsToCloud(updated)
        }
    }

    fun setChatColor(color: ChatColorTheme) {
        val updated = preferences.value.copy(chatColor = color)
        viewModelScope.launch {
            prefsRepo.setChatColor(color)
            syncPrefsToCloud(updated)
        }
    }

    fun setHighContrast(enabled: Boolean) {
        val updated = preferences.value.copy(highContrast = enabled)
        viewModelScope.launch {
            prefsRepo.setHighContrast(enabled)
            syncPrefsToCloud(updated)
        }
    }

    fun setTextSizePreset(preset: TextSizePreset) {
        val updated = preferences.value.copy(textSizePreset = preset)
        viewModelScope.launch {
            prefsRepo.setTextSizePreset(preset)
            syncPrefsToCloud(updated)
        }
    }

    fun setEasyMode(enabled: Boolean) {
        val updated = preferences.value.copy(easyMode = enabled)
        viewModelScope.launch {
            prefsRepo.setEasyMode(enabled)
            syncPrefsToCloud(updated)
        }
    }

    fun setTtsVoice(voice: GeminiTtsVoice) {
        val updated = preferences.value.copy(ttsVoice = voice)
        viewModelScope.launch {
            prefsRepo.setTtsVoice(voice)
            syncPrefsToCloud(updated)
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        callTimerJob?.cancel()
        micMeterJob?.cancel()
        ttsJob?.cancel()
        ttsService.stop()
        audioEngine.stopCallMicMetering()
        audioEngine.stopVoiceNotePlayback()
    }

    companion object {
        private const val TAG = "MainViewModel"
        private const val STOP_TIMEOUT_MS = 5000L
    }
}
