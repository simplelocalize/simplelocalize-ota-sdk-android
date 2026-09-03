package io.simplelocalize.ota

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

/** Engine behaviour: language resolution, namespaces, fallbacks, caching, error handling. */
class TranslationEngineTest {

  /** Scripted stand-in for the HTTP layer; records every request the engine makes. */
  private class FakeLoader : TranslationLoader() {
    val requests = CopyOnWriteArrayList<Pair<String, String?>>()
    var respond: (url: String, etag: String?) -> Outcome = { _, _ -> Outcome.NotFound }
    var failWith: IOException? = null

    override fun load(url: String, etag: String?): Outcome {
      requests.add(url to etag)
      failWith?.let { throw it }
      return respond(url, etag)
    }
  }

  private lateinit var cacheDirectory: java.io.File
  private lateinit var loader: FakeLoader

  @Before
  fun setUp() {
    cacheDirectory = Files.createTempDirectory("slengine").toFile()
    loader = FakeLoader()
  }

  @After
  fun tearDown() {
    cacheDirectory.deleteRecursively()
  }

  private fun configuration(
    language: String? = "pl",
    fallbackLanguage: String? = null,
    namespaces: List<String> = emptyList()
  ) = SimpleLocalizeConfiguration(
    projectToken = "TOKEN",
    environment = "_production",
    namespaces = namespaces,
    language = language,
    fallbackLanguage = fallbackLanguage
  )

  private fun engine(
    configuration: SimpleLocalizeConfiguration = configuration(),
    loader: TranslationLoader = this.loader
  ) = TranslationEngine(configuration, cacheDirectory, loader, preferredLanguagesProvider = { listOf("pl-PL") })

  private fun updated(vararg entries: Pair<String, String>, etag: String? = "\"v1\"") =
    TranslationLoader.Outcome.Updated(entries.toMap(), etag)

  @Test
  fun `downloads and serves translations`() {
    loader.respond = { _, _ -> updated("home.title" to "Cześć") }
    val engine = engine()

    assertTrue(engine.refresh().isSuccess)
    assertEquals("Cześć", engine.getString("home.title"))
    assertEquals("pl", engine.currentLanguage)
    assertEquals("https://cdn.simplelocalize.io/TOKEN/_production/pl", loader.requests.single().first)
  }

  @Test
  fun `second refresh sends the cached etag and keeps content on 304`() {
    loader.respond = { _, etag ->
      if (etag == "\"v1\"") TranslationLoader.Outcome.NotModified else updated("home.title" to "Cześć")
    }
    val engine = engine()
    assertTrue(engine.refresh().isSuccess)
    val revision = engine.revision

    assertEquals(false, engine.refresh().getOrThrow())
    assertEquals("Cześć", engine.getString("home.title"))
    assertEquals(revision, engine.revision)
    assertEquals("\"v1\"", loader.requests.last().second)
  }

  @Test
  fun `refresh is throttled unless forced`() {
    loader.respond = { _, _ -> updated("home.title" to "Cześć") }
    val engine = engine()
    engine.refresh()

    assertEquals(false, engine.refresh(force = false).getOrThrow())
    assertEquals(1, loader.requests.size)
  }

  @Test
  fun `starts from disk cache when network fails`() {
    loader.respond = { _, _ -> updated("home.title" to "Cześć") }
    assertTrue(engine().refresh().isSuccess)

    val offline = FakeLoader().apply { failWith = IOException("offline") }
    val second = engine(loader = offline)
    assertTrue(second.loadFromCache())
    assertEquals("Cześć", second.getString("home.title"))

    assertTrue(second.refresh().isFailure)
    assertEquals("Cześć", second.getString("home.title"))
  }

  @Test
  fun `unpublished language reports failure`() {
    loader.respond = { _, _ -> TranslationLoader.Outcome.NotFound }
    val engine = engine(configuration(language = "xx"))

    assertTrue(engine.refresh().isFailure)
    assertNull(engine.currentLanguage)
  }

  @Test
  fun `resolves language from device locales`() {
    loader.respond = { url, _ ->
      if (url.endsWith("/pl")) updated("home.title" to "Cześć") else TranslationLoader.Outcome.NotFound
    }
    val engine = engine(configuration(language = null))

    assertTrue(engine.refresh().isSuccess)
    assertEquals("pl", engine.currentLanguage)
    // pl_PL and pl-PL are probed before falling back to the plain language key.
    assertEquals(
      listOf("pl_PL", "pl-PL", "pl"),
      loader.requests.map { it.first.substringAfterLast('/') }
    )
  }

  @Test
  fun `namespaces map to tables`() {
    loader.respond = { url, _ ->
      when {
        url.endsWith("/pl/checkout") -> updated("pay" to "Zapłać")
        url.endsWith("/pl/common") -> updated("ok" to "OK")
        else -> TranslationLoader.Outcome.NotFound
      }
    }
    val engine = engine(configuration(namespaces = listOf("common", "checkout")))

    assertTrue(engine.refresh().isSuccess)
    assertEquals("Zapłać", engine.getString("pay", "checkout"))
    assertEquals("OK", engine.getString("ok", "common"))
    assertEquals("Zapłać", engine.getString("pay"))
  }

  @Test
  fun `fallback language fills missing keys`() {
    loader.respond = { url, _ ->
      if (url.endsWith("/pl")) updated("home.title" to "Cześć")
      else updated("home.title" to "Hi", "home.cta" to "Continue")
    }
    val engine = engine(configuration(fallbackLanguage = "en"))

    assertTrue(engine.refresh().isSuccess)
    assertEquals("Cześć", engine.getString("home.title"))
    assertEquals("Continue", engine.getString("home.cta"))
  }

  @Test
  fun `set language switches content`() {
    loader.respond = { url, _ ->
      if (url.endsWith("/de")) updated("home.title" to "Hallo") else updated("home.title" to "Cześć")
    }
    val engine = engine()
    assertTrue(engine.refresh().isSuccess)
    assertEquals("Cześć", engine.getString("home.title"))

    engine.setLanguage("de")
    assertTrue(engine.refresh().isSuccess)
    assertEquals("de", engine.currentLanguage)
    assertEquals("Hallo", engine.getString("home.title"))
  }
}
