package io.simplelocalize.ota

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Over-the-air translations for Android, backed by SimpleLocalize Translation Hosting.
 *
 * ```kotlin
 * SimpleLocalize.start(this, SimpleLocalizeConfiguration(projectToken = "5a5b..."))
 * textView.text = SimpleLocalize.getString("home.title") ?: getString(R.string.home_title)
 * ```
 */
object SimpleLocalize {

  fun interface OnTranslationsChangedListener {
    fun onTranslationsChanged()
  }

  private val executor = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "simplelocalize-ota").apply { isDaemon = true }
  }
  private val mainHandler = Handler(Looper.getMainLooper())
  private val listeners = CopyOnWriteArrayList<OnTranslationsChangedListener>()

  @Volatile
  private var engine: TranslationEngine? = null

  @Volatile
  private var lifecycleCallbacksRegistered = false

  val isStarted: Boolean get() = engine != null

  /** Language key currently served, e.g. `pl_PL`. Null until the first successful download. */
  val currentLanguage: String? get() = engine?.currentLanguage

  /** Increases whenever the in-memory translations change. */
  val revision: Int get() = engine?.revision ?: 0

  /**
   * Loads cached translations synchronously and starts a background refresh.
   * Safe to call from `Application.onCreate()`.
   */
  @JvmStatic
  fun start(context: Context, configuration: SimpleLocalizeConfiguration) {
    val applicationContext = context.applicationContext
    val directory = File(
      applicationContext.filesDir,
      "simplelocalize-ota/${configuration.projectToken}/${configuration.environment}"
    )
    val engine = TranslationEngine(
      configuration = configuration,
      cacheDirectory = directory,
      preferredLanguagesProvider = { deviceLanguages(applicationContext) }
    )
    this.engine = engine
    if (engine.loadFromCache()) {
      notifyListeners()
    }
    if (configuration.refreshOnForeground) {
      registerForegroundRefresh(applicationContext)
    }
    refresh()
  }

  /** Downloads translations in the background. Conditional requests make no-op refreshes nearly free. */
  @JvmStatic
  @JvmOverloads
  fun refresh(force: Boolean = true, callback: ((Result<Unit>) -> Unit)? = null) {
    val engine = this.engine
    if (engine == null) {
      callback?.invoke(Result.failure(SimpleLocalizeException("SimpleLocalize.start() was not called")))
      return
    }
    executor.execute {
      val result = engine.refresh(force)
      if (result.getOrDefault(false)) {
        notifyListeners()
      }
      callback?.let { mainHandler.post { it(result.map { }) } }
    }
  }

  /** Over-the-air translation, or null when the key was not downloaded. */
  @JvmStatic
  @JvmOverloads
  fun getString(key: String, namespace: String? = null): String? = engine?.getString(key, namespace)

  /** Over-the-air translation with an explicit fallback. */
  @JvmStatic
  @JvmOverloads
  fun getStringOrDefault(key: String, defaultValue: String, namespace: String? = null): String =
    getString(key, namespace) ?: defaultValue

  /** All downloaded translations for the current language and the given namespace. */
  @JvmStatic
  @JvmOverloads
  fun allTranslations(namespace: String = ""): Map<String, String> = engine?.allTranslations(namespace) ?: emptyMap()

  /** Overrides the language at runtime, remembers it across launches and refreshes in the background. */
  @JvmStatic
  @JvmOverloads
  fun setLanguage(language: String?, callback: ((Result<Unit>) -> Unit)? = null) {
    val engine = this.engine ?: return
    engine.setLanguage(language)
    if (engine.loadFromCache()) {
      notifyListeners()
    }
    refresh(force = true, callback = callback)
  }

  /**
   * Wraps a context so that `getString(R.string.key)`, XML layouts and Compose `stringResource`
   * return over-the-air translations. Resource entry names are used as translation keys.
   *
   * ```kotlin
   * override fun attachBaseContext(newBase: Context) {
   *   super.attachBaseContext(SimpleLocalize.wrapContext(newBase))
   * }
   * ```
   */
  @JvmStatic
  fun wrapContext(base: Context): Context = SimpleLocalizeContextWrapper.wrap(base)

  @JvmStatic
  fun addOnTranslationsChangedListener(listener: OnTranslationsChangedListener) {
    listeners.addIfAbsent(listener)
  }

  @JvmStatic
  fun removeOnTranslationsChangedListener(listener: OnTranslationsChangedListener) {
    listeners.remove(listener)
  }

  /** Removes downloaded translations from memory and disk. */
  @JvmStatic
  fun clearCache() {
    engine?.clear()
    notifyListeners()
  }

  private fun notifyListeners() {
    if (listeners.isEmpty()) return
    mainHandler.post { listeners.forEach { it.onTranslationsChanged() } }
  }

  private fun registerForegroundRefresh(context: Context) {
    if (lifecycleCallbacksRegistered) return
    val application = context as? Application ?: return
    application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
      override fun onActivityResumed(activity: Activity) {
        refresh(force = false)
      }

      override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
      override fun onActivityStarted(activity: Activity) = Unit
      override fun onActivityPaused(activity: Activity) = Unit
      override fun onActivityStopped(activity: Activity) = Unit
      override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
      override fun onActivityDestroyed(activity: Activity) = Unit
    })
    lifecycleCallbacksRegistered = true
  }

  private fun deviceLanguages(context: Context): List<String> {
    val configuration = context.resources.configuration
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      val locales = configuration.locales
      (0 until locales.size()).map { locales.get(it).toLanguageTag() }
    } else {
      @Suppress("DEPRECATION")
      listOf(configuration.locale.toLanguageTag())
    }
  }
}
