package com.example.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
import com.example.data.GeminiTtsVoice
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

sealed interface TtsPlaybackState {
    data object Idle : TtsPlaybackState
    data class Synthesizing(val targetId: String) : TtsPlaybackState
    data class Playing(val targetId: String) : TtsPlaybackState
    data class Error(val message: String) : TtsPlaybackState
}

/**
 * Text-to-Speech service powered by Firebase AI Logic using gemini-3.8-flash-tts.
 */
class GeminiTtsService(private val appContext: Context) {
    private val _state = MutableStateFlow<TtsPlaybackState>(TtsPlaybackState.Idle)
    val state: StateFlow<TtsPlaybackState> = _state.asStateFlow()

    private var activeTrack: AudioTrack? = null
    private var activeMediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null

    @OptIn(PublicPreviewAPI::class)
    private fun buildModel(voice: GeminiTtsVoice) =
        Firebase.ai.generativeModel(
            modelName = MODEL_ID,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
                speechConfig = SpeechConfig(
                    voice = Voice(voice.voiceName)
                )
            }
        )

    suspend fun speak(
        targetId: String,
        text: String,
        voice: GeminiTtsVoice
    ) {
        val cleanText = text.trim().take(2000)
        if (cleanText.isBlank()) {
            _state.value = TtsPlaybackState.Error("Nothing to read aloud.")
            return
        }

        stop()
        _state.value = TtsPlaybackState.Synthesizing(targetId)

        try {
            val response = withTimeout(TIMEOUT_MS) {
                val model = buildModel(voice)
                model.generateContent(cleanText)
            }

            val parts = response.candidates.firstOrNull()?.content?.parts.orEmpty()
            val audioPart = parts.filterIsInstance<InlineDataPart>().firstOrNull()
            if (audioPart == null || audioPart.inlineData.isEmpty()) {
                _state.value = TtsPlaybackState.Error("Gemini TTS returned no audio data.")
                return
            }

            _state.value = TtsPlaybackState.Playing(targetId)
            playAudioBytes(audioPart.inlineData, audioPart.mimeType)
            if (_state.value is TtsPlaybackState.Playing) {
                _state.value = TtsPlaybackState.Idle
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini TTS timed out", e)
            _state.value = TtsPlaybackState.Error("Speech synthesis timed out. Please try again.")
        } catch (e: CancellationException) {
            _state.value = TtsPlaybackState.Idle
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini TTS failed", e)
            _state.value = TtsPlaybackState.Error(
                e.localizedMessage ?: "Unable to synthesize speech."
            )
        }
    }

    private suspend fun playAudioBytes(bytes: ByteArray, mimeType: String) = withContext(Dispatchers.IO) {
        val lowerMime = mimeType.lowercase()
        if (lowerMime.contains("mp3") || lowerMime.contains("mpeg") || lowerMime.contains("ogg") || lowerMime.contains("aac")) {
            val ext = if (lowerMime.contains("ogg")) "ogg" else "mp3"
            val tempFile = File.createTempFile("gemini_tts_", ".$ext", appContext.cacheDir)
            try {
                tempFile.writeBytes(bytes)
                coroutineScope {
                    val player = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        setDataSource(tempFile.absolutePath)
                        prepare()
                        start()
                    }
                    activeMediaPlayer = player
                    while (player.isPlaying) {
                        delay(100)
                    }
                    player.release()
                    activeMediaPlayer = null
                }
            } finally {
                tempFile.delete()
            }
            return@withContext
        }

        // PCM / L16 / WAV playback via AudioTrack
        val isWavHeader = bytes.size > 44 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte()

        val sampleRate = parseSampleRate(lowerMime) ?: if (isWavHeader) readWavSampleRate(bytes) else 24000
        val offset = if (isWavHeader) 44 else 0
        val pcmLength = bytes.size - offset
        if (pcmLength <= 0) return@withContext

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

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

        activeTrack = track
        try {
            track.play()
            var written = 0
            while (written < pcmLength) {
                val chunk = minOf(minBuf, pcmLength - written)
                val res = track.write(bytes, offset + written, chunk)
                if (res <= 0) break
                written += res
            }
            val durationMs = ((pcmLength / 2.0) / sampleRate * 1000.0).toLong().coerceAtMost(1500L)
            delay(minOf(durationMs, 400L))
        } finally {
            try {
                track.stop()
            } catch (_: Exception) {
            }
            track.release()
            if (activeTrack == track) {
                activeTrack = null
            }
        }
    }

    private fun parseSampleRate(mimeType: String): Int? {
        val match = Regex("rate=(\\d+)").find(mimeType) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }

    private fun readWavSampleRate(wav: ByteArray): Int {
        return try {
            (wav[24].toInt() and 0xFF) or
                ((wav[25].toInt() and 0xFF) shl 8) or
                ((wav[26].toInt() and 0xFF) shl 16) or
                ((wav[27].toInt() and 0xFF) shl 24)
        } catch (_: Exception) {
            24000
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            activeTrack?.pause()
            activeTrack?.flush()
            activeTrack?.release()
        } catch (_: Exception) {
        }
        activeTrack = null
        try {
            activeMediaPlayer?.stop()
            activeMediaPlayer?.release()
        } catch (_: Exception) {
        }
        activeMediaPlayer = null
        _state.value = TtsPlaybackState.Idle
    }

    fun clearError() {
        if (_state.value is TtsPlaybackState.Error) {
            _state.value = TtsPlaybackState.Idle
        }
    }

    companion object {
        private const val TAG = "GeminiTtsService"
        const val MODEL_ID = "gemini-3.1-flash-tts-preview"
        private const val TIMEOUT_MS = 45_000L
    }
}
