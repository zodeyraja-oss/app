package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.crypto.E2eeCryptoManager
import com.example.crypto.IdentityKeys
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

class ChatRepository(
    private val db: FirebaseFirestore,
    private val crypto: E2eeCryptoManager,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        crypto = E2eeCryptoManager(context.applicationContext)
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun getLocalIdentity(uid: String): IdentityKeys = crypto.getOrCreateIdentityKeys(uid)

    fun buildShareCode(uid: String): String {
        val clean = uid.filter { it.isLetterOrDigit() }.uppercase()
        val suffix = clean.take(6).padEnd(6, 'X')
        return "KIN$suffix"
    }

    private fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun emailDirectoryHash(email: String): String {
        return sha256Hex(email.trim().lowercase())
    }

    private fun buildNameKey(rawName: String): String {
        val normalized = rawName.trim().lowercase().replace(Regex("\\s+"), " ")
        return "name_${sha256Hex(normalized).take(32)}"
    }

    private fun nameKeysForDisplayName(displayName: String): List<String> {
        val normalized = displayName.trim().lowercase().replace(Regex("\\s+"), " ")
        if (normalized.isBlank()) return emptyList()
        val firstToken = normalized.substringBefore(" ")
        return listOfNotNull(
            normalized,
            firstToken.takeIf { it.length >= 2 && it != normalized }
        ).map { "name_${sha256Hex(it).take(32)}" }.distinct()
    }

    private suspend fun upsertDirectoryEntries(
        uid: String,
        displayName: String,
        shareCode: String,
        email: String,
        identity: IdentityKeys
    ) {
        val entryPayload = mapOf(
            "userId" to uid,
            "displayName" to displayName,
            "shareCode" to shareCode,
            "publicKeyBase64" to identity.publicKeyBase64,
            "keyFingerprint" to identity.keyFingerprint,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        // Invite code record (zero-PII)
        val invitePayload = mapOf(
            "userId" to uid,
            "displayName" to displayName,
            "publicKeyBase64" to identity.publicKeyBase64,
            "keyFingerprint" to identity.keyFingerprint,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val inviteRef = db.collection("invite_codes").document(shareCode)
        val inviteSnap = inviteRef.get().await()
        if (!inviteSnap.exists()) {
            inviteRef.set(invitePayload).await()
        } else {
            inviteRef.update(invitePayload).await()
        }

        // Hashed email directory (zero-PII)
        if (email.contains("@")) {
            val emailRef = db.collection("email_directory").document(emailDirectoryHash(email))
            val emailSnap = emailRef.get().await()
            if (!emailSnap.exists()) {
                emailRef.set(entryPayload).await()
            } else {
                emailRef.update(entryPayload).await()
            }
        }

        // Hashed name lookup (zero-PII)
        nameKeysForDisplayName(displayName).forEach { nameKey ->
            val memberRef = db.collection("name_lookup")
                .document(nameKey)
                .collection("members")
                .document(uid)
            val snap = memberRef.get().await()
            if (!snap.exists()) {
                memberRef.set(entryPayload).await()
            } else {
                memberRef.update(entryPayload).await()
            }
        }
    }

    suspend fun ensureUserProvisionedAndVault(): Result<UserPrivate> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val user = auth.currentUser!!
        var currentPath = "users/$uid"
        var currentOp = OperationType.GET
        return try {
            val identity = crypto.getOrCreateIdentityKeys(uid)
            val fallbackName = (user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")
                ?: "KinChat User").take(80)
            val email = (user.email ?: "private@kinchat.local").take(254)
            val shareCode = buildShareCode(uid)

            val userRef = db.collection("users").document(uid)
            currentPath = "users/$uid"
            currentOp = OperationType.GET
            val existingSnap = userRef.get().await()
            val effectiveDisplayName: String
            if (!existingSnap.exists()) {
                effectiveDisplayName = fallbackName
                val payload = mapOf(
                    "userId" to uid,
                    "email" to email,
                    "displayName" to effectiveDisplayName,
                    "shareCode" to shareCode,
                    "publicKeyBase64" to identity.publicKeyBase64,
                    "keyFingerprint" to identity.keyFingerprint,
                    "appColor" to AppColorTheme.EMERALD.name,
                    "chatColor" to ChatColorTheme.MATCH_APP.name,
                    "themeMode" to ThemeMode.SYSTEM.name,
                    "highContrast" to false,
                    "textSizePreset" to TextSizePreset.COMFORT.name,
                    "easyMode" to false,
                    "ttsVoice" to GeminiTtsVoice.KORE.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                currentOp = OperationType.CREATE
                userRef.set(payload).await()
            } else {
                effectiveDisplayName = existingSnap.getString("displayName")
                    ?.takeIf { it.isNotBlank() }
                    ?.take(80)
                    ?: fallbackName
                currentOp = OperationType.UPDATE
                userRef.update(
                    mapOf(
                        "displayName" to effectiveDisplayName,
                        "publicKeyBase64" to identity.publicKeyBase64,
                        "keyFingerprint" to identity.keyFingerprint,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            currentPath = "directory/$uid"
            currentOp = OperationType.WRITE
            upsertDirectoryEntries(
                uid = uid,
                displayName = effectiveDisplayName,
                shareCode = shareCode,
                email = email,
                identity = identity
            )

            // Query participant chats via allowed list rule (never .get() a non-existent chat doc)
            val vaultId = "vault_${uid.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(64)}"
            currentPath = "chats"
            currentOp = OperationType.LIST
            val existingVaults = db.collection("chats")
                .whereArrayContains("participantUids", uid)
                .get()
                .await()
            val vaultSnap = existingVaults.documents.firstOrNull {
                it.id == vaultId || it.getString("chatType") == "vault"
            }

            val vaultRef = db.collection("chats").document(vaultId)
            if (vaultSnap == null) {
                currentPath = "chats/$vaultId"
                currentOp = OperationType.CREATE
                val roomKey = crypto.generateRoomAesKey()
                crypto.cacheRoomKey(vaultId, roomKey)
                val wrapped = crypto.wrapRoomKeyForParticipant(identity, identity.publicKeyBase64, roomKey)
                val previewJson = DecryptedMessage.encodePayloadJson("Personal end-to-end encrypted vault ready.")
                val encPreview = crypto.encryptText(roomKey, previewJson)
                val chatPayload = mapOf(
                    "title" to "My Encrypted Vault",
                    "chatType" to "vault",
                    "creatorId" to uid,
                    "participantUids" to listOf(uid),
                    "wrappedKeys" to mapOf(uid to wrapped),
                    "lastMessageCiphertext" to encPreview.ciphertextBase64,
                    "lastMessageIv" to encPreview.ivBase64,
                    "lastMessageType" to "text",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                vaultRef.set(chatPayload).await()
            } else {
                val room = vaultSnap.toObject(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                room?.let { resolveRoomKey(uid, it) }
            }

            currentPath = "users/$uid"
            currentOp = OperationType.GET
            val updatedSnap = userRef.get().await()
            val profile = updatedSnap.toObject(UserPrivate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserPrivate(
                    userId = uid,
                    email = email,
                    displayName = effectiveDisplayName,
                    shareCode = shareCode,
                    publicKeyBase64 = identity.publicKeyBase64,
                    keyFingerprint = identity.keyFingerprint
                )
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, currentOp, currentPath)
            Result.failure(e)
        }
    }

    suspend fun updateDisplayName(newNameRaw: String, previousName: String?): Result<UserPrivate> {
        val cleanName = newNameRaw.trim().take(80)
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Name cannot be empty."))
        }
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val user = auth.currentUser!!
        val userPath = "users/$uid"
        return try {
            val identity = crypto.getOrCreateIdentityKeys(uid)
            val shareCode = buildShareCode(uid)
            val email = (user.email ?: "private@kinchat.local").take(254)

            // Sync Firebase Auth profile
            try {
                user.updateProfile(
                    userProfileChangeRequest { displayName = cleanName }
                ).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ChatRepository", "Auth displayName update warning: ${e.message}")
            }

            val userRef = db.collection("users").document(uid)
            userRef.update(
                mapOf(
                    "displayName" to cleanName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Remove stale name_lookup keys if name changed
            val oldKeys = previousName?.let { nameKeysForDisplayName(it) }.orEmpty().toSet()
            val newKeys = nameKeysForDisplayName(cleanName).toSet()
            (oldKeys - newKeys).forEach { staleKey ->
                try {
                    db.collection("name_lookup").document(staleKey)
                        .collection("members").document(uid)
                        .delete().await()
                } catch (_: Exception) {
                }
            }

            upsertDirectoryEntries(
                uid = uid,
                displayName = cleanName,
                shareCode = shareCode,
                email = email,
                identity = identity
            )

            val snap = userRef.get().await()
            val updated = snap.toObject(UserPrivate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserPrivate(
                    userId = uid,
                    email = email,
                    displayName = cleanName,
                    shareCode = shareCode,
                    publicKeyBase64 = identity.publicKeyBase64,
                    keyFingerprint = identity.keyFingerprint
                )
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userPath)
            Result.failure(e)
        }
    }

    suspend fun syncPreferencesToAccount(prefs: AccessibilityPreferences): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val userPath = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "appColor" to prefs.appColor.name,
                    "chatColor" to prefs.chatColor.name,
                    "themeMode" to prefs.themeMode.name,
                    "highContrast" to prefs.highContrast,
                    "textSizePreset" to prefs.textSizePreset.name,
                    "easyMode" to prefs.easyMode,
                    "ttsVoice" to prefs.ttsVoice.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userPath)
            Result.failure(e)
        }
    }

    suspend fun searchPeers(queryRaw: String): Result<List<DirectoryEntry>> {
        val clean = queryRaw.trim()
        if (clean.length < 2) {
            return Result.failure(IllegalArgumentException("Enter a full email, name, or invite code."))
        }
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required."))
        return try {
            val found = linkedMapOf<String, DirectoryEntry>()

            if (clean.contains("@")) {
                val hash = emailDirectoryHash(clean)
                val snap = db.collection("email_directory").document(hash).get().await()
                if (snap.exists()) {
                    snap.toObject(DirectoryEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?.takeIf { it.userId.isNotBlank() && it.userId != uid }
                        ?.let { found[it.userId] = it }
                }
            } else {
                // 1. Check exact normalized name lookup
                val nameKey = buildNameKey(clean)
                val nameSnap = db.collection("name_lookup")
                    .document(nameKey)
                    .collection("members")
                    .get()
                    .await()
                nameSnap.toObjects(DirectoryEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.userId.isNotBlank() && it.userId != uid }
                    .forEach { found[it.userId] = it }

                // 2. Also check exact invite code if query matches invite code format
                val codeCandidate = clean.uppercase().replace(Regex("[^A-Z0-9_-]"), "")
                if (codeCandidate.length in 4..32) {
                    val inviteSnap = db.collection("invite_codes").document(codeCandidate).get().await()
                    if (inviteSnap.exists()) {
                        inviteSnap.toObject(InviteCodeRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?.takeIf { it.userId.isNotBlank() && it.userId != uid }
                            ?.let { rec ->
                                found[rec.userId] = DirectoryEntry(
                                    id = rec.userId,
                                    userId = rec.userId,
                                    displayName = rec.displayName,
                                    shareCode = codeCandidate,
                                    publicKeyBase64 = rec.publicKeyBase64,
                                    keyFingerprint = rec.keyFingerprint,
                                    updatedAt = rec.updatedAt
                                )
                            }
                    }
                }
            }

            Result.success(found.values.toList())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "directory_search")
            Result.failure(e)
        }
    }

    suspend fun addContactFromDirectory(entry: DirectoryEntry): Result<Contact> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required."))
        if (entry.userId.isBlank() || entry.userId == uid) {
            return Result.failure(IllegalArgumentException("Cannot add yourself as a peer contact."))
        }
        val path = "users/$uid/contacts/${entry.userId}"
        return try {
            val contactRef = db.collection("users").document(uid)
                .collection("contacts").document(entry.userId)
            val existingContact = contactRef.get().await()
            val safeCode = entry.shareCode.ifBlank { buildShareCode(entry.userId) }.take(32)
            if (!existingContact.exists()) {
                contactRef.set(
                    mapOf(
                        "contactUid" to entry.userId,
                        "displayName" to entry.displayName.take(80),
                        "shareCode" to safeCode,
                        "publicKeyBase64" to entry.publicKeyBase64,
                        "keyFingerprint" to entry.keyFingerprint,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                contactRef.update(
                    mapOf(
                        "displayName" to entry.displayName.take(80),
                        "publicKeyBase64" to entry.publicKeyBase64,
                        "keyFingerprint" to entry.keyFingerprint
                    )
                ).await()
            }
            Result.success(
                Contact(
                    id = entry.userId,
                    contactUid = entry.userId,
                    displayName = entry.displayName,
                    shareCode = safeCode,
                    publicKeyBase64 = entry.publicKeyBase64,
                    keyFingerprint = entry.keyFingerprint
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeChats(uid: String): Flow<List<ChatRoom>> = flow {
        require(uid.isNotBlank()) { "User ID required" }
        val path = "chats"
        emitAll(
            db.collection("chats")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeContacts(uid: String): Flow<List<Contact>> = flow {
        require(uid.isNotBlank()) { "User ID required" }
        val path = "users/$uid/contacts"
        emitAll(
            db.collection("users").document(uid).collection("contacts")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Contact::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.displayName.lowercase() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMessages(uid: String, chatId: String): Flow<List<EncryptedMessageDoc>> = flow {
        require(uid.isNotBlank() && chatId.isNotBlank()) { "User ID and Chat ID required" }
        val path = "chats/$chatId/messages"
        emitAll(
            db.collection("chats").document(chatId).collection("messages")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(EncryptedMessageDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeActiveCalls(uid: String): Flow<List<VoiceCallDoc>> = flow {
        require(uid.isNotBlank()) { "User ID required" }
        val path = "calls"
        emitAll(
            db.collection("calls")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(VoiceCallDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.status == "ringing" || it.status == "active" }
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createChatRoom(
        title: String,
        chatType: String,
        contacts: List<Contact>
    ): Result<String> {
        val cleanTitle = title.trim().take(80)
        if (cleanTitle.isBlank()) {
            return Result.failure(IllegalArgumentException("Chat title cannot be empty."))
        }
        val path = "chats"
        return try {
            val uid = requireUserId()
            val identity = crypto.getOrCreateIdentityKeys(uid)
            val roomId = "chat_${UUID.randomUUID().toString().replace("-", "").take(20)}"
            val roomKey = crypto.generateRoomAesKey()
            crypto.cacheRoomKey(roomId, roomKey)

            val participantUids = (listOf(uid) + contacts.map { it.contactUid }).distinct().take(20)
            val wrappedKeys = mutableMapOf<String, String>()
            wrappedKeys[uid] = crypto.wrapRoomKeyForParticipant(identity, identity.publicKeyBase64, roomKey)
            contacts.forEach { contact ->
                if (contact.publicKeyBase64.isNotBlank()) {
                    wrappedKeys[contact.contactUid] = crypto.wrapRoomKeyForParticipant(
                        identity,
                        contact.publicKeyBase64,
                        roomKey
                    )
                }
            }

            val initJson = DecryptedMessage.encodePayloadJson("End-to-end encrypted chat created.")
            val encPreview = crypto.encryptText(roomKey, initJson)
            val payload = mapOf(
                "title" to cleanTitle,
                "chatType" to chatType,
                "creatorId" to uid,
                "participantUids" to participantUids,
                "wrappedKeys" to wrappedKeys,
                "lastMessageCiphertext" to encPreview.ciphertextBase64,
                "lastMessageIv" to encPreview.ivBase64,
                "lastMessageType" to "text",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("chats").document(roomId).set(payload).await()
            Result.success(roomId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun resolveRoomKey(uid: String, chat: ChatRoom): ByteArray? {
        val cached = crypto.getCachedRoomKey(chat.id)
        if (cached != null) return cached
        val wrapped = chat.wrappedKeys[uid] ?: return null
        val identity = crypto.getOrCreateIdentityKeys(uid)
        val unwrapped = crypto.unwrapRoomKey(identity, wrapped) ?: return null
        crypto.cacheRoomKey(chat.id, unwrapped)
        return unwrapped
    }

    fun decryptChatPreview(uid: String, chat: ChatRoom): String {
        val roomKey = resolveRoomKey(uid, chat) ?: return "End-to-end encrypted"
        val json = crypto.decryptText(roomKey, chat.lastMessageCiphertext, chat.lastMessageIv)
            ?: return "Encrypted message"
        return try {
            val obj = org.json.JSONObject(json)
            when (chat.lastMessageType) {
                "image" -> "Photo: ${obj.optString("text", "Encrypted image")}"
                "location" -> "Location: ${obj.optString("locationLabel", "Map pin")}"
                "youtube" -> "Video: ${obj.optString("youtubeTitle", "YouTube video")}"
                "voice" -> "Voice Note (${obj.optInt("voiceDurationSec", 0)}s)"
                "call" -> obj.optString("text", "Voice call")
                else -> obj.optString("text", "Encrypted message")
            }
        } catch (_: Exception) {
            json
        }
    }

    fun decryptMessage(uid: String, chat: ChatRoom, doc: EncryptedMessageDoc): DecryptedMessage {
        val roomKey = resolveRoomKey(uid, doc.chatId.let { chat })
        val jsonString = if (roomKey != null) {
            crypto.decryptText(roomKey, doc.ciphertext, doc.iv)
        } else {
            null
        }
        return DecryptedMessage.fromDecryptedJson(doc, jsonString)
    }

    suspend fun sendEncryptedMessage(
        chat: ChatRoom,
        messageType: String,
        payloadJson: String,
        senderDisplayName: String? = null
    ): Result<Unit> {
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val path = "chats/${chat.id}/messages/$msgId"
        return try {
            val uid = requireUserId()
            val identity = crypto.getOrCreateIdentityKeys(uid)
            val senderName = (senderDisplayName?.takeIf { it.isNotBlank() }
                ?: auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
                ?: "Me").take(80)
            val roomKey = resolveRoomKey(uid, chat)
                ?: return Result.failure(IllegalStateException("Missing encryption key for this chat."))

            val encrypted = crypto.encryptText(roomKey, payloadJson)
            if (encrypted.ciphertextBase64.length > 740_000) {
                return Result.failure(IllegalArgumentException("Encrypted media exceeds safe message size limit."))
            }

            val msgPayload = mapOf(
                "chatId" to chat.id,
                "senderId" to uid,
                "senderName" to senderName,
                "participantUids" to chat.participantUids,
                "messageType" to messageType,
                "ciphertext" to encrypted.ciphertextBase64,
                "iv" to encrypted.ivBase64,
                "keyFingerprint" to identity.keyFingerprint,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("chats").document(chat.id)
                .collection("messages").document(msgId)
                .set(msgPayload)
                .await()

            val previewJson = when (messageType) {
                "image" -> DecryptedMessage.encodePayloadJson("Shared an encrypted photo")
                "voice" -> DecryptedMessage.encodePayloadJson("Sent an encrypted voice note")
                else -> payloadJson.take(1000)
            }
            val encPreview = crypto.encryptText(roomKey, previewJson)
            db.collection("chats").document(chat.id)
                .update(
                    mapOf(
                        "lastMessageCiphertext" to encPreview.ciphertextBase64.take(1900),
                        "lastMessageIv" to encPreview.ivBase64,
                        "lastMessageType" to messageType,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun startVoiceCall(chat: ChatRoom, callerDisplayName: String? = null): Result<VoiceCallDoc> {
        val callId = "call_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val path = "calls/$callId"
        return try {
            val uid = requireUserId()
            val callerName = (callerDisplayName?.takeIf { it.isNotBlank() }
                ?: auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
                ?: "KinChat User").take(80)
            val roomKey = resolveRoomKey(uid, chat) ?: crypto.generateRoomAesKey()
            val sasCode = crypto.deriveCallSasCode(roomKey, callId)
            val payload = mapOf(
                "chatId" to chat.id,
                "callerId" to uid,
                "callerName" to callerName,
                "participantUids" to chat.participantUids,
                "status" to "active",
                "sasCode" to sasCode,
                "durationSeconds" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("calls").document(callId).set(payload).await()
            Result.success(
                VoiceCallDoc(
                    id = callId,
                    chatId = chat.id,
                    callerId = uid,
                    callerName = callerName,
                    participantUids = chat.participantUids,
                    status = "active",
                    sasCode = sasCode,
                    durationSeconds = 0L
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun endVoiceCall(callId: String, durationSeconds: Long, status: String = "ended"): Result<Unit> {
        val path = "calls/$callId"
        return try {
            requireUserId()
            val safeDuration = durationSeconds.coerceIn(0L, 86400L)
            db.collection("calls").document(callId)
                .update(
                    mapOf(
                        "status" to status,
                        "durationSeconds" to safeDuration,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
