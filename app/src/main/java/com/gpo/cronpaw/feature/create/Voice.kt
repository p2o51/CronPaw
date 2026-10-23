package com.gpo.cronpaw.feature.create

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.gpo.cronpaw.core.notion.NotionClient
import com.gpo.cronpaw.core.settings.AppSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Listening that only ends when the user says so (the stop button), never on a pause.
 * [text] is what has been heard so far; [stop] returns the final text.
 */
interface Voice {
    val text: StateFlow<String>
    /** 0..1, for the level ring. */
    val level: StateFlow<Float>
    val error: StateFlow<String?>
    fun start()
    suspend fun stop(): String
    fun cancel()

    companion object {
        /** ElevenLabs Scribe when the user brought a key, else the system recogniser. */
        fun create(context: Context, scope: CoroutineScope): Voice =
            if (AppSettings.elevenLabsKey.value.isNotBlank()) ScribeVoice(context, scope) else SystemVoice(context)
    }
}

/**
 * The system [SpeechRecognizer]. It ends a session on every pause, so each result is
 * kept and a new session starts straight away until [stop].
 */
class SystemVoice(private val context: Context) : Voice {
    private val _text = MutableStateFlow("")
    override val text: StateFlow<String> = _text.asStateFlow()
    private val _level = MutableStateFlow(0f)
    override val level: StateFlow<Float> = _level.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var committed = ""
    private var stopping = false
    private var finished: CompletableDeferred<Unit>? = null

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10_000L)
    }

    override fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _error.value = "这台设备没有语音识别"
            return
        }
        stopping = false
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply { setRecognitionListener(listener) }
        recognizer?.startListening(intent)
    }

    private fun join(a: String, b: String) = if (a.isEmpty()) b else if (b.isEmpty()) a else "$a $b"

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) {
            _level.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            _text.value = join(committed, p)
        }

        override fun onResults(results: Bundle?) {
            val r = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            committed = join(committed, r)
            _text.value = committed
            next()
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> next()
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { _error.value = "需要麦克风权限"; done() }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> {
                    _error.value = "语音识别连不上"; done()
                }
                else -> if (stopping) done() else next()
            }
        }
    }

    /** One session ended: start another, unless the user pressed stop. */
    private fun next() {
        if (stopping) { done(); return }
        recognizer?.cancel()
        recognizer?.startListening(intent)
    }

    private fun done() {
        _level.value = 0f
        finished?.complete(Unit)
    }

    override suspend fun stop(): String {
        stopping = true
        finished = CompletableDeferred()
        recognizer?.stopListening()
        withTimeoutOrNull(2_500) { finished?.await() }
        recognizer?.destroy()
        recognizer = null
        return _text.value.trim()
    }

    override fun cancel() {
        stopping = true
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }
}

/**
 * ElevenLabs Scribe with the user's own key: record locally, send once on stop
 * (POST /v1/speech-to-text). Nothing is heard until then, so [text] stays empty while
 * recording and [level] carries the feedback.
 */
class ScribeVoice(private val context: Context, private val scope: CoroutineScope) : Voice {
    private val _text = MutableStateFlow("")
    override val text: StateFlow<String> = _text.asStateFlow()
    private val _level = MutableStateFlow(0f)
    override val level: StateFlow<Float> = _level.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var meter: Job? = null
    private val file = File(context.cacheDir, "voice.m4a")

    override fun start() {
        runCatching {
            file.delete()
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(48_000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
        }.onFailure { _error.value = "麦克风打不开" }
        meter = scope.launch {
            while (isActive && recorder != null) {
                val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                _level.value = (amp / 12_000f).coerceIn(0f, 1f)
                delay(80)
            }
        }
    }

    override suspend fun stop(): String {
        meter?.cancel()
        _level.value = 0f
        val r = recorder ?: return ""
        recorder = null
        runCatching { r.stop() }
        r.release()
        return runCatching { transcribe() }
            .onFailure { _error.value = it.message ?: "Scribe 没有回应" }
            .getOrDefault("")
            .also { _text.value = it }
    }

    private suspend fun transcribe(): String = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model_id", AppSettings.scribeModel.value.ifBlank { "scribe_v2" })
            .addFormDataPart("tag_audio_events", "false")
            .addFormDataPart("file", "voice.m4a", file.asRequestBody("audio/mp4".toMediaType()))
            .build()
        val req = Request.Builder()
            .url("https://api.elevenlabs.io/v1/speech-to-text")
            .header("xi-api-key", AppSettings.elevenLabsKey.value.trim())
            .post(body)
            .build()
        http.newCall(req).execute().use { res ->
            val text = res.body.string()
            val obj = runCatching { NotionClient.json.parseToJsonElement(text).jsonObject }.getOrNull()
            if (!res.isSuccessful) {
                val detail = obj?.get("detail")
                val msg = runCatching { detail?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull }.getOrNull()
                    ?: runCatching { detail?.jsonPrimitive?.contentOrNull }.getOrNull()
                throw IllegalStateException(msg ?: "Scribe HTTP ${res.code}")
            }
            obj?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        }
    }

    override fun cancel() {
        meter?.cancel()
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
    }

    private companion object {
        val http: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
