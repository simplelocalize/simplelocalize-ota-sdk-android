package io.simplelocalize.ota

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * Exercises the real HTTP layer (HttpURLConnection) against a minimal socket server,
 * so conditional requests are verified end to end and not only mocked.
 */
class TranslationLoaderTest {

  private lateinit var serverSocket: ServerSocket
  private lateinit var baseUrl: String
  private val receivedRequests = CopyOnWriteArrayList<String>()

  @Volatile
  private var responder: (request: String) -> String = { "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n" }

  @Before
  fun setUp() {
    serverSocket = ServerSocket(0)
    baseUrl = "http://127.0.0.1:${serverSocket.localPort}"
    thread(isDaemon = true) {
      while (!serverSocket.isClosed) {
        try {
          serverSocket.accept().use { socket ->
            val reader = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
            val request = StringBuilder()
            while (true) {
              val line = reader.readLine() ?: break
              if (line.isEmpty()) break
              request.append(line).append('\n')
            }
            val text = request.toString()
            receivedRequests.add(text)
            socket.getOutputStream().use { it.write(responder(text).toByteArray(StandardCharsets.UTF_8)) }
          }
        } catch (error: IOException) {
          return@thread
        }
      }
    }
  }

  @After
  fun tearDown() {
    serverSocket.close()
  }

  private fun response(status: String, body: String? = null, etag: String? = null): String {
    val builder = StringBuilder("HTTP/1.1 $status\r\n")
    etag?.let { builder.append("ETag: $it\r\n") }
    builder.append("Content-Type: application/json\r\n")
    builder.append("Connection: close\r\n")
    if (body == null) {
      builder.append("Content-Length: 0\r\n\r\n")
    } else {
      val bytes = body.toByteArray(StandardCharsets.UTF_8)
      builder.append("Content-Length: ${bytes.size}\r\n\r\n").append(body)
    }
    return builder.toString()
  }

  @Test
  fun `downloads translations and reads the etag`() {
    responder = { response("200 OK", """{"home.title":"Cześć"}""", "\"v1\"") }

    val outcome = TranslationLoader().load("$baseUrl/TOKEN/_production/pl", null)

    outcome as TranslationLoader.Outcome.Updated
    assertEquals("Cześć", outcome.translations["home.title"])
    assertEquals("\"v1\"", outcome.etag)
    assertTrue(receivedRequests.single().contains("GET /TOKEN/_production/pl HTTP/1.1"))
  }

  @Test
  fun `sends if none match and reports not modified`() {
    responder = { request ->
      if (request.contains("If-None-Match: \"v1\"")) response("304 Not Modified") else response("200 OK", "{}", "\"v1\"")
    }

    val outcome = TranslationLoader().load("$baseUrl/TOKEN/_production/pl", "\"v1\"")

    assertEquals(TranslationLoader.Outcome.NotModified, outcome)
  }

  @Test
  fun `missing resource is reported as not found`() {
    responder = { response("404 Not Found") }

    assertEquals(
      TranslationLoader.Outcome.NotFound,
      TranslationLoader().load("$baseUrl/TOKEN/_production/xx", null)
    )
  }

  @Test(expected = IOException::class)
  fun `server error is raised`() {
    responder = { response("500 Internal Server Error") }

    TranslationLoader().load("$baseUrl/TOKEN/_production/pl", null)
  }
}
