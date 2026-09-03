package io.simplelocalize.ota

import org.json.JSONObject
import java.io.File

/**
 * Persists downloaded resources so the app starts with the last known translations, even offline,
 * and so refreshes can be revalidated with `If-None-Match`.
 */
internal class TranslationCache(private val directory: File) {

  data class Entry(val etag: String?, val updatedAt: Long, val translations: Map<String, String>)

  @Synchronized
  fun read(resource: TranslationResource): Entry? {
    val file = File(directory, resource.cacheFileName())
    if (!file.exists()) return null
    return try {
      val root = JSONObject(file.readText())
      Entry(
        etag = root.optString("etag").ifEmpty { null },
        updatedAt = root.optLong("updatedAt"),
        translations = TranslationJson.parse(root.getJSONObject("translations").toString())
      )
    } catch (error: Exception) {
      file.delete()
      null
    }
  }

  @Synchronized
  fun write(entry: Entry, resource: TranslationResource) {
    directory.mkdirs()
    val root = JSONObject()
    entry.etag?.let { root.put("etag", it) }
    root.put("updatedAt", entry.updatedAt)
    root.put("translations", TranslationJson.write(entry.translations))
    val target = File(directory, resource.cacheFileName())
    val temporary = File(directory, resource.cacheFileName() + ".tmp")
    temporary.writeText(root.toString())
    if (!temporary.renameTo(target)) {
      target.writeText(root.toString())
      temporary.delete()
    }
  }

  @Synchronized
  fun readLanguage(): String? {
    val file = File(directory, LANGUAGE_FILE)
    return if (file.exists()) file.readText().trim().ifEmpty { null } else null
  }

  @Synchronized
  fun writeLanguage(language: String?) {
    directory.mkdirs()
    val file = File(directory, LANGUAGE_FILE)
    if (language == null) file.delete() else file.writeText(language)
  }

  @Synchronized
  fun clear() {
    directory.deleteRecursively()
  }

  private companion object {
    const val LANGUAGE_FILE = "resolved-language.txt"
  }
}
