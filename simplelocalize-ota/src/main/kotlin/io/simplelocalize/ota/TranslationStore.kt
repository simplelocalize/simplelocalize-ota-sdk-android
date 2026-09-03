package io.simplelocalize.ota

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread safe in-memory storage of downloaded translations.
 *
 * Lookups happen on the UI thread while rendering, so they must stay synchronous and cheap.
 */
internal class TranslationStore {

  private val tables = ConcurrentHashMap<String, ConcurrentHashMap<String, Map<String, String>>>()
  private val revisionCounter = AtomicInteger(0)

  val revision: Int get() = revisionCounter.get()

  /** Replaces the content of one resource. Returns true when anything actually changed. */
  fun replace(language: String, namespace: String, translations: Map<String, String>): Boolean {
    val namespaces = tables.getOrPut(language) { ConcurrentHashMap() }
    if (namespaces[namespace] == translations) {
      return false
    }
    namespaces[namespace] = translations
    revisionCounter.incrementAndGet()
    return true
  }

  fun lookup(key: String, namespace: String, languages: List<String>): String? {
    for (language in languages) {
      val value = tables[language]?.get(namespace)?.get(key)
      if (!value.isNullOrEmpty()) {
        return value
      }
    }
    return null
  }

  fun translations(language: String, namespace: String = ""): Map<String, String> =
    tables[language]?.get(namespace) ?: emptyMap()

  fun clear() {
    tables.clear()
    revisionCounter.incrementAndGet()
  }
}
