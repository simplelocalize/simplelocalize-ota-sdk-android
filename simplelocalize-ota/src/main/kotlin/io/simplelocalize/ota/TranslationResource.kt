package io.simplelocalize.ota

/** A single downloadable file on Translation Hosting. */
internal data class TranslationResource(
  val language: String,
  /** Empty string means the default resource (no namespace). */
  val namespace: String = "",
  val customerId: String? = null
) {

  /** Path relative to the CDN base URL, e.g. `TOKEN/_production/pl_PL/common`. */
  fun path(projectToken: String, environment: String): String {
    val languageComponent = if (customerId.isNullOrEmpty()) language else "${language}_$customerId"
    val components = mutableListOf(projectToken, environment, languageComponent)
    if (namespace.isNotEmpty()) {
      components.add(namespace)
    }
    return components.joinToString("/")
  }

  fun url(projectToken: String, environment: String, baseUrl: String): String =
    baseUrl.trimEnd('/') + "/" + path(projectToken, environment)

  /** Stable, file system safe name used by the on-disk cache. */
  fun cacheFileName(): String {
    val builder = StringBuilder(language)
    if (!customerId.isNullOrEmpty()) {
      builder.append('_').append(customerId)
    }
    if (namespace.isNotEmpty()) {
      builder.append('.').append(namespace.replace('/', '-'))
    }
    val sanitized = builder.toString().map { character ->
      if (character.isLetterOrDigit() || character == '-' || character == '_' || character == '.') character else '-'
    }.joinToString("")
    return "$sanitized.json"
  }
}
