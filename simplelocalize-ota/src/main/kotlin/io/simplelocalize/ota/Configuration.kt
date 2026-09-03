package io.simplelocalize.ota

/**
 * Configuration of the Translation Hosting (CDN) source.
 *
 * All values are public information - the project token is meant to be shipped inside
 * client apps, exactly like it is on the web.
 */
data class SimpleLocalizeConfiguration @JvmOverloads constructor(
  /** Project token from Settings -> Credentials. */
  val projectToken: String,
  /** Hosting environment key: `_production`, `_latest` or a custom environment key. */
  val environment: String = "_production",
  /** CDN base URL; change it when publishing to a custom hosting provider. */
  val baseUrl: String = DEFAULT_BASE_URL,
  /** Namespaces to download. Empty means the default (no namespace) resource. */
  val namespaces: List<String> = emptyList(),
  /** Forces a language key instead of resolving it from the device locale. */
  val language: String? = null,
  /** Language used when a key is missing in the current language. */
  val fallbackLanguage: String? = null,
  /** Customer identifier for customer specific translations (`{language}_{customerId}`). */
  val customerId: String? = null,
  /** Minimum time between two automatic refreshes; manual refreshes ignore it. */
  val minimumRefreshIntervalMillis: Long = 10 * 60 * 1000L,
  /** Refresh translations when the app returns to the foreground. */
  val refreshOnForeground: Boolean = true,
  /** Optional diagnostics sink. */
  val logger: ((String) -> Unit)? = null
) {
  companion object {
    const val DEFAULT_BASE_URL = "https://cdn.simplelocalize.io"
  }
}

class SimpleLocalizeException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
