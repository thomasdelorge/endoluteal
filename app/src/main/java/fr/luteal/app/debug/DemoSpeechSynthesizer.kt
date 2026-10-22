package fr.luteal.app.debug

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import fr.luteal.app.BuildConfig
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object DemoSpeechSynthesizer {
    private const val UTTERANCE_ID = "endoluteal_demo_consultation"

    val demoScript: String =
        """
        Médecin : Bonjour, que puis-je faire pour vous aujourd'hui ?
        Patiente : J'ai des douleurs pelviennes depuis six mois, surtout pendant les règles.
        Médecin : Avez-vous déjà essayé un traitement ?
        Patiente : Oui, de l'ibuprofène, avec un soulagement seulement partiel.
        """.trimIndent()

    suspend fun synthesizeConsultationWav(context: Context, output: File): Boolean {
        if (!BuildConfig.DEBUG) return false
        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { cont ->
                var tts: TextToSpeech? = null
                val finished = AtomicBoolean(false)
                fun finish(result: Boolean) {
                    if (!finished.compareAndSet(false, true)) return
                    tts?.shutdown()
                    if (cont.isActive) cont.resume(result)
                }
                tts = TextToSpeech(context.applicationContext) { status ->
                    if (status != TextToSpeech.SUCCESS) {
                        finish(false)
                        return@TextToSpeech
                    }
                    val engine = tts ?: run {
                        finish(false)
                        return@TextToSpeech
                    }
                    val lang = engine.setLanguage(Locale.FRENCH)
                    if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                        finish(false)
                        return@TextToSpeech
                    }
                    output.parentFile?.mkdirs()
                    if (output.exists()) output.delete()
                    engine.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) = Unit

                            override fun onDone(utteranceId: String?) {
                                if (utteranceId == UTTERANCE_ID) {
                                    finish(output.exists() && output.length() > 0)
                                }
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                if (utteranceId == UTTERANCE_ID) finish(false)
                            }

                            override fun onError(utteranceId: String?, errorCode: Int) {
                                if (utteranceId == UTTERANCE_ID) finish(false)
                            }
                        }
                    )
                    val params = Bundle()
                    val result = engine.synthesizeToFile(
                        demoScript,
                        params,
                        output,
                        UTTERANCE_ID
                    )
                    if (result != TextToSpeech.SUCCESS) {
                        finish(false)
                    }
                }
                cont.invokeOnCancellation {
                    tts?.stop()
                    tts?.shutdown()
                }
            }
        }
    }
}
