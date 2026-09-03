package io.simplelocalize.ota

import java.io.File
import java.util.Locale

/**
 * Platform independent core: resolves the language, downloads resources, keeps the cache
 * in sync and answers lookups. All network calls are blocking - the Android facade runs
 * them on a background executor.
 */
internal class TranslationEngine(
  private val configuration: SimpleLocalizeConfiguration,
  cacheDirectory: File,
  private val loader: TranslationLoader = TranslationLoader(),
  private val preferredLanguagesProvider: () -> List<String> = { listOf(Locale.getDefault().toLanguageTag()) }
) {

  private val cache = TranslationCache(cacheDirectory)
  private val store = TranslationStore()

  @Volatile
  private var resolvedLanguage: String? = configuration.language ?: cache.readLanguage()

  @Volatile
  private var lastRefreshAt: Long = 0

  val revision: Int get() = store.revision

  val currentLanguage: String? get() = resolvedLanguage

  private val namespaces: List<String>
    get() = if (configuration.namespaces.isEmpty()) listOf("") else configuration.namespaces

  private val languages: List<String>
    get() {
      val language = resolvedLanguage ?: return emptyList()
      val fallback = configuration.fallbackLanguage
      return if (fallback == null || fallback == language) listOf(language) else listOf(language, fallback)
    }

  /** Loads previously downloaded resources from disk. Returns true when content changed. */
  fun loadFromCache(): Boolean {
    var changed = false
    for (language in languages) {
      for (namespace in namespaces) {
        val resource = TranslationResource(language, namespace, configuration.customerId)
        val entry = cache.read(resource) ?: continue
        changed = store.replace(language, namespace, entry.translations) || changed
      }
    }
    return changed
  }

  /**
   * Downloads translations.
   * @return true when the in-memory content changed.
   */
  fun refresh(force: Boolean = true): Result<Boolean> {
    if (!force && System.currentTimeMillis() - lastRefreshAt < configuration.minimumRefreshIntervalMillis) {
      return Result.success(false)
    }

    var changed = false
    var loadedAnyResource = false
    var sawNotFound = false
    var firstError: Throwable? = null

    if (resolvedLanguage == null) {
      val candidates = LanguageResolver.candidates(configuration.language, preferredLanguagesProvider())
      configuration.logger?.invoke("Resolving language, candidates: ${candidates.joinToString(", ")}")
      for (candidate in candidates) {
        val resource = TranslationResource(candidate, namespaces.first(), configuration.customerId)
        when (val outcome = download(resource)) {
          is DownloadResult.Success -> {
            resolvedLanguage = candidate
            cache.writeLanguage(candidate)
            changed = changed || outcome.changed
            loadedAnyResource = true
          }
          DownloadResult.NotFound -> sawNotFound = true
          is DownloadResult.Failure -> return Result.failure(outcome.error)
        }
        if (resolvedLanguage != null) break
      }
      if (resolvedLanguage == null) {
        return Result.failure(SimpleLocalizeException("None of the languages ${candidates.joinToString(", ")} is published"))
      }
      changed = loadFromCache() || changed
    }

    val currentLanguage = resolvedLanguage ?: return Result.failure(SimpleLocalizeException("Language not resolved"))

    for (language in languages) {
      for (namespace in namespaces) {
        if (loadedAnyResource && language == currentLanguage && namespace == namespaces.first()) {
          continue // already downloaded while resolving the language
        }
        val resource = TranslationResource(language, namespace, configuration.customerId)
        when (val outcome = download(resource)) {
          is DownloadResult.Success -> {
            changed = changed || outcome.changed
            loadedAnyResource = true
          }
          DownloadResult.NotFound -> {
            sawNotFound = true
            configuration.logger?.invoke("Resource not published: ${resource.path(configuration.projectToken, configuration.environment)}")
          }
          is DownloadResult.Failure -> if (firstError == null) firstError = outcome.error
        }
      }
    }

    firstError?.let { return Result.failure(it) }
    // Nothing at all is published for this language - do not pretend the refresh succeeded.
    if (!loadedAnyResource && sawNotFound) {
      resolvedLanguage = null
      cache.writeLanguage(null)
      return Result.failure(SimpleLocalizeException("None of the languages ${languages.joinToString(", ")} is published"))
    }
    lastRefreshAt = System.currentTimeMillis()
    return Result.success(changed)
  }

  fun getString(key: String, namespace: String? = null): String? {
    val languages = languages
    if (languages.isEmpty()) return null
    for (candidate in searchNamespaces(namespace)) {
      store.lookup(key, candidate, languages)?.let { return it }
    }
    return null
  }

  fun allTranslations(namespace: String = ""): Map<String, String> {
    val language = resolvedLanguage ?: return emptyMap()
    return store.translations(language, namespace)
  }

  fun setLanguage(language: String?) {
    resolvedLanguage = language
    lastRefreshAt = 0
    cache.writeLanguage(language)
  }

  fun clear() {
    store.clear()
    cache.clear()
  }

  private fun searchNamespaces(requested: String?): List<String> {
    val primary = if (!requested.isNullOrEmpty()) requested else namespaces.first()
    val result = mutableListOf(primary)
    configuration.namespaces.forEach { if (!result.contains(it)) result.add(it) }
    return result
  }

  private sealed class DownloadResult {
    data class Success(val changed: Boolean) : DownloadResult()
    object NotFound : DownloadResult()
    data class Failure(val error: Throwable) : DownloadResult()
  }

  private fun download(resource: TranslationResource): DownloadResult {
    val url = resource.url(configuration.projectToken, configuration.environment, configuration.baseUrl)
    val cached = cache.read(resource)
    return try {
      when (val outcome = loader.load(url, cached?.etag)) {
        is TranslationLoader.Outcome.Updated -> {
          configuration.logger?.invoke("Downloaded ${outcome.translations.size} keys from $url")
          val changed = store.replace(resource.language, resource.namespace, outcome.translations)
          cache.write(
            TranslationCache.Entry(outcome.etag, System.currentTimeMillis(), outcome.translations),
            resource
          )
          DownloadResult.Success(changed)
        }
        TranslationLoader.Outcome.NotModified -> {
          configuration.logger?.invoke("Not modified: $url")
          val changed = cached?.let { store.replace(resource.language, resource.namespace, it.translations) } ?: false
          DownloadResult.Success(changed)
        }
        TranslationLoader.Outcome.NotFound -> DownloadResult.NotFound
      }
    } catch (error: Exception) {
      configuration.logger?.invoke("Download failed for $url: ${error.message}")
      DownloadResult.Failure(error)
    }
  }
}
