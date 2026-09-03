package io.simplelocalize.ota

/**
 * Maps device locales to SimpleLocalize language keys.
 *
 * Android reports BCP 47 tags such as `en-GB`, while SimpleLocalize keys are usually
 * `en`, `pl`, `en_GB` or `pt-BR`. The resolver returns ordered candidates; the first one
 * published on the CDN wins.
 */
internal object LanguageResolver {

  fun candidates(explicit: String?, preferredLanguages: List<String>): List<String> {
    if (!explicit.isNullOrEmpty()) {
      return listOf(explicit)
    }
    val result = LinkedHashSet<String>()
    preferredLanguages.forEach { tag -> result.addAll(variants(tag)) }
    return result.toList()
  }

  /** `en-GB` -> [`en_GB`, `en-GB`, `en`] */
  fun variants(tag: String): List<String> {
    val parts = tag.replace('_', '-').split('-').filter { it.isNotEmpty() }
    val language = parts.firstOrNull()?.lowercase() ?: return emptyList()
    val region = parts.drop(1).firstOrNull { it.length == 2 || it.all(Char::isDigit) }?.uppercase()
    val result = mutableListOf<String>()
    if (region != null) {
      result.add("${language}_$region")
      result.add("$language-$region")
    }
    result.add(language)
    return result
  }
}
