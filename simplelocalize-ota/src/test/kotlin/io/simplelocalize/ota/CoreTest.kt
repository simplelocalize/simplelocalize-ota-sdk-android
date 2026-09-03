package io.simplelocalize.ota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TranslationJsonTest {

  @Test
  fun `parses flat payload`() {
    val translations = TranslationJson.parse("""{"home.title":"Cześć","home.subtitle":"Witaj"}""")
    assertEquals("Cześć", translations["home.title"])
    assertEquals(2, translations.size)
  }

  @Test
  fun `flattens nested payload`() {
    val translations = TranslationJson.parse("""{"home":{"title":"Cześć","cta":{"label":"Dalej"}},"count":7}""")
    assertEquals("Cześć", translations["home.title"])
    assertEquals("Dalej", translations["home.cta.label"])
    assertEquals("7", translations["count"])
  }

  @Test(expected = SimpleLocalizeException::class)
  fun `rejects non object payload`() {
    TranslationJson.parse("[1,2,3]")
  }
}

class TranslationResourceTest {

  @Test
  fun `default resource path`() {
    val resource = TranslationResource("pl_PL")
    assertEquals("TOKEN/_production/pl_PL", resource.path("TOKEN", "_production"))
  }

  @Test
  fun `namespace and customer path`() {
    val resource = TranslationResource("en", "checkout", "acme")
    assertEquals("TOKEN/_latest/en_acme/checkout", resource.path("TOKEN", "_latest"))
  }

  @Test
  fun `resource url`() {
    val resource = TranslationResource("en", "checkout")
    assertEquals(
      "https://cdn.simplelocalize.io/TOKEN/_production/en/checkout",
      resource.url("TOKEN", "_production", "https://cdn.simplelocalize.io/")
    )
  }

  @Test
  fun `cache file name is file system safe`() {
    val resource = TranslationResource("pt-BR", "emails/transactional", "acme")
    assertEquals("pt-BR_acme.emails-transactional.json", resource.cacheFileName())
  }
}

class LanguageResolverTest {

  @Test
  fun `explicit language wins`() {
    assertEquals(listOf("de_DE"), LanguageResolver.candidates("de_DE", listOf("pl-PL")))
  }

  @Test
  fun `candidates from preferred languages`() {
    assertEquals(
      listOf("en_GB", "en-GB", "en", "pl_PL", "pl-PL", "pl"),
      LanguageResolver.candidates(null, listOf("en-GB", "pl-PL"))
    )
  }

  @Test
  fun `script subtag is skipped`() {
    assertEquals(listOf("zh_CN", "zh-CN", "zh"), LanguageResolver.variants("zh-Hans-CN"))
  }
}

class TranslationStoreTest {

  @Test
  fun `lookup falls back to second language`() {
    val store = TranslationStore()
    store.replace("pl", "", mapOf("a" to "A-pl"))
    store.replace("en", "", mapOf("a" to "A-en", "b" to "B-en"))
    assertEquals("A-pl", store.lookup("a", "", listOf("pl", "en")))
    assertEquals("B-en", store.lookup("b", "", listOf("pl", "en")))
    assertNull(store.lookup("c", "", listOf("pl", "en")))
  }

  @Test
  fun `empty translation is treated as missing`() {
    val store = TranslationStore()
    store.replace("pl", "", mapOf("a" to ""))
    store.replace("en", "", mapOf("a" to "A-en"))
    assertEquals("A-en", store.lookup("a", "", listOf("pl", "en")))
  }

  @Test
  fun `revision changes only on real change`() {
    val store = TranslationStore()
    assertTrue(store.replace("pl", "", mapOf("a" to "A")))
    val revision = store.revision
    assertFalse(store.replace("pl", "", mapOf("a" to "A")))
    assertEquals(revision, store.revision)
  }
}

class TranslationCacheTest {

  @Test
  fun `write and read round trip`() {
    val directory = Files.createTempDirectory("slcache").toFile()
    val cache = TranslationCache(directory)
    val resource = TranslationResource("pl")
    cache.write(TranslationCache.Entry("\"abc\"", 123L, mapOf("a" to "A")), resource)

    val entry = cache.read(resource)
    assertEquals("\"abc\"", entry?.etag)
    assertEquals("A", entry?.translations?.get("a"))

    cache.writeLanguage("pl")
    assertEquals("pl", cache.readLanguage())

    cache.clear()
    assertNull(cache.read(resource))
    assertFalse(File(directory, resource.cacheFileName()).exists())
  }
}
