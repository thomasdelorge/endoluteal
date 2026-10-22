package fr.luteal.core.openrouter

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonObject

class OpenRouterClient(
    private val apiKey: String,
    private val appReferer: String = "https://endoluteal.local"
) {
    fun transcribeMultipart(
        audioFile: File,
        mimeType: String,
        model: String,
        language: String,
        diarization: SttDiarizationRequest?
    ): String {
        val boundary = "----Endoluteal${UUID.randomUUID()}"
        val connection = openPost(OpenRouterDefaults.TRANSCRIPTIONS_URL, boundary)
        connection.outputStream.use { raw ->
            val writer = MultipartWriter(raw, boundary)
            writer.writeField("model", model)
            writer.writeField("language", language)
            if (diarization != null) {
                writer.writeField("response_format", "verbose_json")
                when (diarization.mode) {
                    SttDiarizationMode.TOP_LEVEL_DIARIZE -> {
                        writer.writeField("diarize", "true")
                        writer.writeField("timestamp_granularities[]", "segment")
                        writer.writeField("timestamp_granularities[]", "word")
                    }
                    SttDiarizationMode.AZURE_MAI_TRANSCRIBE_2 -> {
                        writer.writeField("timestamp_granularities[]", "segment")
                        writer.writeField("timestamp_granularities[]", "word")
                        writer.writeField(
                            "provider",
                            """{"options":{"azure":{"diarization":{"enabled":true}}}}"""
                        )
                    }
                    SttDiarizationMode.NONE -> Unit
                }
            }
            val fileName = audioFile.name
            writer.writeFile("file", fileName, mimeType, audioFile.readBytes())
            writer.finish()
        }
        return readResponse(connection)
    }

    fun transcribeJsonBase64(
        audioBytes: ByteArray,
        format: String,
        model: String,
        language: String,
        diarization: SttDiarizationRequest?
    ): String {
        val connection = openJsonPost(OpenRouterDefaults.TRANSCRIPTIONS_URL)
        val body = buildJsonObject {
            put("model", model)
            put("language", language)
            putJsonObject("input_audio") {
                put("data", android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP))
                put("format", format)
            }
            if (diarization != null) {
                put("response_format", "verbose_json")
                when (diarization.mode) {
                    SttDiarizationMode.TOP_LEVEL_DIARIZE -> {
                        put("diarize", true)
                        putJsonArray("timestamp_granularities") {
                            add(JsonPrimitive("segment"))
                            add(JsonPrimitive("word"))
                        }
                    }
                    SttDiarizationMode.AZURE_MAI_TRANSCRIBE_2 -> {
                        putJsonArray("timestamp_granularities") {
                            add(JsonPrimitive("segment"))
                            add(JsonPrimitive("word"))
                        }
                        putJsonObject("provider") {
                            putJsonObject("options") {
                                putJsonObject("azure") {
                                    putJsonObject("diarization") {
                                        put("enabled", true)
                                    }
                                }
                            }
                        }
                    }
                    SttDiarizationMode.NONE -> Unit
                }
            }
        }
        writeJsonBody(connection, body.toString())
        return readResponse(connection)
    }

    fun summarize(model: String, systemPrompt: String, userPrompt: String): String {
        val connection = openJsonPost(OpenRouterDefaults.CHAT_URL)
        val body = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", systemPrompt)
                    }
                )
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", userPrompt)
                    }
                )
            }
        }
        writeJsonBody(connection, body.toString())
        return readResponse(connection)
    }

    private fun openPost(url: String, boundary: String): HttpURLConnection {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 90_000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("HTTP-Referer", appReferer)
            setRequestProperty("X-Title", "Endoluteal")
        }
        return connection
    }

    private fun openJsonPost(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 90_000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("HTTP-Referer", appReferer)
            setRequestProperty("X-Title", "Endoluteal")
        }
    }

    private fun writeJsonBody(connection: HttpURLConnection, jsonBody: String) {
        connection.outputStream.use { out ->
            out.write(jsonBody.toByteArray(StandardCharsets.UTF_8))
        }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            val message = runCatching {
                Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject
                    ?.get("message")?.jsonPrimitive?.contentOrNull
            }.getOrNull() ?: "HTTP $code"
            throw OpenRouterException(message)
        }
        return body
    }

    private class MultipartWriter(
        private val out: java.io.OutputStream,
        private val boundary: String
    ) {
        private val charset = StandardCharsets.UTF_8

        fun writeField(name: String, value: String) {
            out.write("--$boundary\r\n".toByteArray(charset))
            out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray(charset))
            out.write(value.toByteArray(charset))
            out.write("\r\n".toByteArray(charset))
        }

        fun writeFile(name: String, fileName: String, mimeType: String, bytes: ByteArray) {
            out.write("--$boundary\r\n".toByteArray(charset))
            out.write(
                "Content-Disposition: form-data; name=\"$name\"; filename=\"$fileName\"\r\n"
                    .toByteArray(charset)
            )
            out.write("Content-Type: $mimeType\r\n\r\n".toByteArray(charset))
            out.write(bytes)
            out.write("\r\n".toByteArray(charset))
        }

        fun finish() {
            out.write("--$boundary--\r\n".toByteArray(charset))
        }
    }
}

data class SttDiarizationRequest(
    val mode: SttDiarizationMode
)
