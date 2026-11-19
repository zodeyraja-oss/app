package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import org.json.JSONObject

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserPrivate(
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val shareCode: String = "",
    val publicKeyBase64: String = "",
    val keyFingerprint: String = "",
    val appColor: String? = null,
    val chatColor: String? = null,
    val themeMode: String? = null,
    val highContrast: Boolean? = null,
    val textSizePreset: String? = null,
    val easyMode: Boolean? = null,
    val ttsVoice: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class InviteCodeRecord(
    @DocumentId val shareCode: String = "",
    val userId: String = "",
    val displayName: String = "",
    val publicKeyBase64: String = "",
    val keyFingerprint: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DirectoryEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val shareCode: String = "",
    val publicKeyBase64: String = "",
    val keyFingerprint: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Contact(
    @DocumentId val id: String = "",
    val contactUid: String = "",
    val displayName: String = "",
    val shareCode: String = "",
    val publicKeyBase64: String = "",
    val keyFingerprint: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatRoom(
    @DocumentId val id: String = "",
    val title: String = "",
    val chatType: String = "direct",
    val creatorId: String = "",
    val participantUids: List<String> = emptyList(),
    val wrappedKeys: Map<String, String> = emptyMap(),
    val lastMessageCiphertext: String = "",
    val lastMessageIv: String = "",
    val lastMessageType: String = "none",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class EncryptedMessageDoc(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val participantUids: List<String> = emptyList(),
    val messageType: String = "text",
    val ciphertext: String = "",
    val iv: String = "",
    val keyFingerprint: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VoiceCallDoc(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val participantUids: List<String> = emptyList(),
    val status: String = "ringing",
    val sasCode: String = "",
    val durationSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Decrypted client-side message payload (never stored in plaintext in Firestore).
 */
data class DecryptedMessage(
    val id: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val messageType: String,
    val text: String,
    val imageBase64: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String? = null,
    val youtubeVideoId: String? = null,
    val youtubeTitle: String? = null,
    val voiceAudioBase64: String? = null,
    val voiceDurationSec: Int = 0,
    val keyFingerprint: String = "",
    val rawCiphertextPreview: String = "",
    val decryptedSuccessfully: Boolean = true,
    val createdAt: Timestamp? = null
) {
    fun toSpeechText(): String = when (messageType) {
        "text" -> "$senderName says: $text"
        "image" -> "$senderName shared an encrypted photo. ${if (text.isNotBlank()) text else ""}"
        "location" -> "$senderName shared a location: ${locationLabel ?: "Pinned map spot"}."
        "youtube" -> "$senderName shared a YouTube video: ${youtubeTitle ?: text}."
        "voice" -> "$senderName sent a $voiceDurationSec second voice note."
        "call" -> "$senderName: $text"
        else -> text
    }

    companion object {
        fun encodePayloadJson(
            text: String = "",
            imageBase64: String? = null,
            latitude: Double? = null,
            longitude: Double? = null,
            locationLabel: String? = null,
            youtubeVideoId: String? = null,
            youtubeTitle: String? = null,
            voiceAudioBase64: String? = null,
            voiceDurationSec: Int = 0
        ): String {
            return JSONObject().apply {
                put("text", text)
                if (!imageBase64.isNullOrBlank()) put("imageBase64", imageBase64)
                if (latitude != null) put("latitude", latitude)
                if (longitude != null) put("longitude", longitude)
                if (!locationLabel.isNullOrBlank()) put("locationLabel", locationLabel)
                if (!youtubeVideoId.isNullOrBlank()) put("youtubeVideoId", youtubeVideoId)
                if (!youtubeTitle.isNullOrBlank()) put("youtubeTitle", youtubeTitle)
                if (!voiceAudioBase64.isNullOrBlank()) put("voiceAudioBase64", voiceAudioBase64)
                if (voiceDurationSec > 0) put("voiceDurationSec", voiceDurationSec)
            }.toString()
        }

        fun fromDecryptedJson(
            doc: EncryptedMessageDoc,
            jsonString: String?
        ): DecryptedMessage {
            if (jsonString == null) {
                return DecryptedMessage(
                    id = doc.id,
                    chatId = doc.chatId,
                    senderId = doc.senderId,
                    senderName = doc.senderName,
                    messageType = doc.messageType,
                    text = "Encrypted with an earlier key on another session.",
                    keyFingerprint = doc.keyFingerprint,
                    rawCiphertextPreview = doc.ciphertext.take(36),
                    decryptedSuccessfully = false,
                    createdAt = doc.createdAt
                )
            }
            return try {
                val obj = JSONObject(jsonString)
                DecryptedMessage(
                    id = doc.id,
                    chatId = doc.chatId,
                    senderId = doc.senderId,
                    senderName = doc.senderName,
                    messageType = doc.messageType,
                    text = obj.optString("text", ""),
                    imageBase64 = obj.optString("imageBase64", "").ifBlank { null },
                    latitude = if (obj.has("latitude")) obj.optDouble("latitude") else null,
                    longitude = if (obj.has("longitude")) obj.optDouble("longitude") else null,
                    locationLabel = obj.optString("locationLabel", "").ifBlank { null },
                    youtubeVideoId = obj.optString("youtubeVideoId", "").ifBlank { null },
                    youtubeTitle = obj.optString("youtubeTitle", "").ifBlank { null },
                    voiceAudioBase64 = obj.optString("voiceAudioBase64", "").ifBlank { null },
                    voiceDurationSec = obj.optInt("voiceDurationSec", 0),
                    keyFingerprint = doc.keyFingerprint,
                    rawCiphertextPreview = doc.ciphertext.take(36),
                    decryptedSuccessfully = true,
                    createdAt = doc.createdAt
                )
            } catch (_: Exception) {
                DecryptedMessage(
                    id = doc.id,
                    chatId = doc.chatId,
                    senderId = doc.senderId,
                    senderName = doc.senderName,
                    messageType = doc.messageType,
                    text = jsonString,
                    keyFingerprint = doc.keyFingerprint,
                    rawCiphertextPreview = doc.ciphertext.take(36),
                    decryptedSuccessfully = true,
                    createdAt = doc.createdAt
                )
            }
        }
    }
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    DARK("Black"),
    LIGHT("White")
}

enum class AppColorTheme(val label: String, val swatchHex: Long) {
    EMERALD("Emerald", 0xFF2EE6A8),
    OCEAN("Ocean", 0xFF4DA3FF),
    VIOLET("Violet", 0xFFB388FF),
    AMBER("Amber", 0xFFFFB74D),
    ROSE("Rose", 0xFFFF6E91),
    CYAN("Cyan", 0xFF38E8E8)
}

enum class ChatColorTheme(val label: String, val swatchHex: Long) {
    MATCH_APP("Match App", 0xFF2EE6A8),
    EMERALD("Mint", 0xFF0F6B4E),
    COBALT("Cobalt", 0xFF1958A6),
    PLUM("Plum", 0xFF5A2EA6),
    CORAL("Coral", 0xFF9E304F),
    GOLD("Gold", 0xFF8C5D00)
}

enum class TextSizePreset(val label: String, val scale: Float) {
    COMFORT("Comfort", 1.0f),
    LARGE("Large", 1.22f),
    EXTRA_LARGE("Extra Large", 1.42f)
}

enum class GeminiTtsVoice(val voiceName: String, val displayLabel: String, val tone: String) {
    KORE("Kore", "Kore", "Clear & Firm"),
    PUCK("Puck", "Puck", "Warm & Upbeat"),
    AOEDE("Aoede", "Aoede", "Calm & Gentle"),
    CHARON("Charon", "Charon", "Deep & Steady"),
    FENRIR("Fenrir", "Fenrir", "Bright & Energetic")
}

data class AccessibilityPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appColor: AppColorTheme = AppColorTheme.EMERALD,
    val chatColor: ChatColorTheme = ChatColorTheme.MATCH_APP,
    val highContrast: Boolean = false,
    val textSizePreset: TextSizePreset = TextSizePreset.COMFORT,
    val easyMode: Boolean = false,
    val ttsVoice: GeminiTtsVoice = GeminiTtsVoice.KORE
)
