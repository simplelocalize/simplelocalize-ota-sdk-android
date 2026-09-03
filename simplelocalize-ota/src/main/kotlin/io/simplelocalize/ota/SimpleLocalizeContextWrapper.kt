package io.simplelocalize.ota

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources

/**
 * Context whose [Resources] answer with over-the-air translations before falling back to
 * the strings compiled into the APK.
 */
class SimpleLocalizeContextWrapper private constructor(
  base: Context,
  private val wrappedResources: Resources
) : ContextWrapper(base) {

  override fun getResources(): Resources = wrappedResources

  companion object {
    @JvmStatic
    fun wrap(base: Context): Context {
      if (base is SimpleLocalizeContextWrapper) return base
      return SimpleLocalizeContextWrapper(base, SimpleLocalizeResources(base.resources))
    }
  }
}

/**
 * Resources decorator: a resource id is translated to its entry name (`R.string.home_title` ->
 * `home_title`) and looked up in the over-the-air content first.
 */
@Suppress("DEPRECATION")
internal class SimpleLocalizeResources(
  private val base: Resources
) : Resources(base.assets, base.displayMetrics, base.configuration) {

  override fun getString(id: Int): String = overTheAir(id) ?: super.getString(id)

  override fun getString(id: Int, vararg formatArgs: Any?): String {
    val translation = overTheAir(id) ?: return super.getString(id, *formatArgs)
    return String.format(configuration.locale ?: java.util.Locale.getDefault(), translation, *formatArgs)
  }

  override fun getText(id: Int): CharSequence = overTheAir(id) ?: super.getText(id)

  override fun getText(id: Int, def: CharSequence?): CharSequence? = overTheAir(id) ?: super.getText(id, def)

  private fun overTheAir(id: Int): String? {
    if (!SimpleLocalize.isStarted) return null
    val key = try {
      base.getResourceEntryName(id)
    } catch (error: NotFoundException) {
      return null
    }
    return SimpleLocalize.getString(key)
  }
}
