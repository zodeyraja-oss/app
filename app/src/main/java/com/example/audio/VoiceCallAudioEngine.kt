package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/**
 * Handles real microphone metering for encrypted voice calls, speakerphone routing,
 * and encrypted voice-note recording/playback.
 */
class VoiceCallAudioEngine(private val context: Context) {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _isRecordingNote = MutableStateFlow(false)
    val isRecordingNote: StateFlow<Boolean> = _isRecordingNote.asStateFlow()

    private val _playingVoiceMessageId = MutableStateFlow<String?>(null)
    val playingVoiceMessageId: StateFlow<String?> = _playingVoiceMessageId.asStateFlow()

    @Volatile
    private var callCaptureActive = false

    @Volatile
    private var isMuted = false

    @Volatile
    private var noteRecordingActive = false

    private var activeNoteTrack: AudioTrack? = null

    fun setMuted(muted: Boolean) {
        isMuted = muted
        if (muted) {
            _micLevel.value = 0f
        }
    }

    fun setSpeakerphone(enabled: Boolean) {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = enabled
        } catch (_: Exception) {
        }
    }

    fun resetAudioRouting() {
        try {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun startCallMicMetering() = withContext(Dispatchers.IO) {
        if (callCaptureActive) return@withContext
        callCaptureActive = true
        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        var recorder: AudioRecord? = null
        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                callCaptureActive = false
                return@withContext
            }
            recorder.startRecording()
            val buffer = ShortArray(minBuf / 2)
            while (isActive && callCaptureActive) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0 && !isMuted) {
                    var peak = 0
                    for (i in 0 until read) {
                        val v = abs(buffer[i].toInt())
                        if (v > peak) peak = v
                    }
                    _micLevel.value = (peak / 18000f).coerceIn(0f, 1f)
                } else {
                    _micLevel.value = 0f
                }
                delay(50)
            }
        } catch (_: Exception) {
            _micLevel.value = 0f
        } finally {
            callCaptureActive = false
            _micLevel.value = 0f
            try {
                recorder?.stop()
            } catch (_: Exception) {
            }
            recorder?.release()
        }
    }

    fun stopCallMicMetering() {
        callCaptureActive = false
        _micLevel.value = 0f
        resetAudioRouting()
    }

    /**
     * Records up to 12 seconds of 8kHz 16-bit PCM mono audio for E2EE voice note sharing.
     */
    @SuppressLint("MissingPermission")
    suspend fun recordVoiceNote(): Pair<String, Int>? = withContext(Dispatchers.IO) {
        if (noteRecordingActive) return@withContext null
        noteRecordingActive = true
        _isRecordingNote.value = true

        val sampleRate = 8000
        val maxBytes = sampleRate * 2 * 12 // Max ~192KB raw -> ~256KB Base64
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(1024)

        val out = ByteArrayOutputStream()
        var recorder: AudioRecord? = null
        val startTime = System.currentTimeMillis()
        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                return@withContext null
            }
            recorder.startRecording()
            val buf = ByteArray(minBuf)
            while (isActive && noteRecordingActive && out.size() < maxBytes) {
                val read = recorder.read(buf, 0, buf.size)
                if (read > 0) {
                    out.write(buf, 0, read)
                }
            }
            val bytes = out.toByteArray()
            if (bytes.size < 1600) return@withContext null
            val durationSec = (((System.currentTimeMillis() - startTime) / 1000L).toInt()).coerceAtLeast(1)
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            b64 to durationSec
        } catch (_: Exception) {
            null
        } finally {
            noteRecordingActive = false
            _isRecordingNote.value = false
            try {
                recorder?.stop()
            } catch (_: Exception) {
            }
            recorder?.release()
        }
    }

    fun stopVoiceNoteRecording() {
        noteRecordingActive = false
    }

    suspend fun playVoiceNote(messageId: String, audioBase64: String) = withContext(Dispatchers.IO) {
        stopVoiceNotePlayback()
        val pcmBytes = try {
            Base64.decode(audioBase64, Base64.NO_WRAP)
        } catch (_: Exception) {
            return@withContext
        }
        if (pcmBytes.isEmpty()) return@withContext

        val sampleRate = 8000
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        activeNoteTrack = track
        _playingVoiceMessageId.value = messageId
        try {
            track.play()
            var written = 0
            while (isActive && written < pcmBytes.size && _playingVoiceMessageId.value == messageId) {
                val chunk = minOf(minBuf, pcmBytes.size - written)
                val res = track.write(pcmBytes, written, chunk)
                if (res <= 0) break
                written += res
            }
            delay(200)
        } catch (_: Exception) {
        } finally {
            try {
                track.stop()
            } catch (_: Exception) {
            }
            track.release()
            if (activeNoteTrack == track) activeNoteTrack = null
            if (_playingVoiceMessageId.value == messageId) _playingVoiceMessageId.value = null
        }
    }

    fun stopVoiceNotePlayback() {
        _playingVoiceMessageId.value = null
        try {
            activeNoteTrack?.pause()
            activeNoteTrack?.flush()
            activeNoteTrack?.release()
        } catch (_: Exception) {
        }
        activeNoteTrack = null
    }
}
