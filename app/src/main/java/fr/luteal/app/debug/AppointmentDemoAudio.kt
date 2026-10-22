package fr.luteal.app.debug

import android.content.Context
import fr.luteal.app.BuildConfig
import java.io.File
import java.io.InputStream

object AppointmentDemoAudio {
    private const val ASSET_PATH = "appointment_demo/consultation_demo.wav"
    const val DISPLAY_NAME = "consultation_demo.wav"

    suspend fun openDemoInputStream(context: Context): InputStream? {
        if (!BuildConfig.DEBUG) return null
        val cacheFile = File(context.cacheDir, "appointment-demo/$DISPLAY_NAME")
        cacheFile.parentFile?.mkdirs()
        val synthesized = DemoSpeechSynthesizer.synthesizeConsultationWav(context, cacheFile)
        if (synthesized && cacheFile.exists()) {
            return cacheFile.inputStream()
        }
        return runCatching {
            context.assets.open(ASSET_PATH)
        }.getOrNull()
    }
}
