package io.simplelocalize.ota

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/** Downloads a single hosting resource using a conditional GET. */
internal open class TranslationLoader(
  private val connectTimeoutMillis: Int = 15_000,
  private val readTimeoutMillis: Int = 15_000
) {

  sealed class Outcome {
    data class Updated(val translations: Map<String, String>, val etag: String?) : Outcome()
    object NotModified : Outcome()
    object NotFound : Outcome()
  }

  @Throws(IOException::class)
  open fun load(url: String, etag: String?): Outcome {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      connectTimeout = connectTimeoutMillis
      readTimeout = readTimeoutMillis
      useCaches = false
      setRequestProperty("Accept", "application/json")
      setRequestProperty("Accept-Encoding", "gzip")
      if (!etag.isNullOrEmpty()) {
        setRequestProperty("If-None-Match", etag)
      }
    }
    try {
      return when (val status = connection.responseCode) {
        HttpURLConnection.HTTP_NOT_MODIFIED -> Outcome.NotModified
        HttpURLConnection.HTTP_NOT_FOUND -> Outcome.NotFound
        in 200..299 -> {
          val body = readBody(connection)
          Outcome.Updated(TranslationJson.parse(body), connection.getHeaderField("ETag"))
        }
        else -> throw IOException("Translation Hosting responded with HTTP $status")
      }
    } finally {
      connection.disconnect()
    }
  }

  private fun readBody(connection: HttpURLConnection): String {
    val stream = connection.inputStream
    val decoded = if (connection.contentEncoding?.contains("gzip", ignoreCase = true) == true) {
      GZIPInputStream(stream)
    } else {
      stream
    }
    return decoded.bufferedReader().use { it.readText() }
  }
}
