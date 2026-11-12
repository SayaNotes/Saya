package com.xnotes.math

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Talks only to a user-deployed HTTPS proxy; provider secrets never enter the Android app. */
object MathStudioAiClient {
    fun explain(endpoint: String, problem: String, calculation: String): String {
        val url = URL(endpoint.trim())
        require(url.protocol.equals("https", ignoreCase = true)) { "The AI endpoint must use HTTPS." }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 45000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val body = JSONObject().put("problem", problem).put("calculation", calculation)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("AI service returned HTTP $code: ${response.take(300)}")
            val answer = JSONObject(response).optString("answer")
            require(answer.isNotBlank()) { "The AI service returned an empty explanation." }
            return answer
        } finally {
            connection.disconnect()
        }
    }
}
