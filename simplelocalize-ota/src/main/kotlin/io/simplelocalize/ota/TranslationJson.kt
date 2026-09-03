package io.simplelocalize.ota

import org.json.JSONObject

/**
 * Parses Translation Hosting payloads.
 *
 * Hosting publishes either flat (`{"home.title": "Hi"}`) or nested (`{"home": {"title": "Hi"}}`)
 * JSON depending on project settings; both are flattened to dot separated keys.
 */
internal object TranslationJson {

  fun parse(payload: String): Map<String, String> {
    val root = try {
      JSONObject(payload)
    } catch (error: Exception) {
      throw SimpleLocalizeException("Unexpected response from Translation Hosting", error)
    }
    val result = LinkedHashMap<String, String>()
    flatten(root, "", result)
    return result
  }

  fun write(translations: Map<String, String>): JSONObject = JSONObject(translations as Map<*, *>)

  private fun flatten(node: JSONObject, prefix: String, result: MutableMap<String, String>) {
    for (key in node.keys()) {
      val path = if (prefix.isEmpty()) key else "$prefix.$key"
      when (val value = node.get(key)) {
        is JSONObject -> flatten(value, path, result)
        is String -> result[path] = value
        is Number, is Boolean -> result[path] = value.toString()
        else -> Unit
      }
    }
  }
}
