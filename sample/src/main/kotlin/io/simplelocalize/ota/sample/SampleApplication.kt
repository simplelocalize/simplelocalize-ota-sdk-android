package io.simplelocalize.ota.sample

import android.app.Application
import io.simplelocalize.ota.SimpleLocalize
import io.simplelocalize.ota.SimpleLocalizeConfiguration

/** Replace with the project token from Settings -> Credentials in SimpleLocalize. */
const val PROJECT_TOKEN = "YOUR_PROJECT_TOKEN"

class SampleApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    SimpleLocalize.start(
      this,
      SimpleLocalizeConfiguration(
        projectToken = PROJECT_TOKEN,
        environment = "_latest",
        fallbackLanguage = "en"
      )
    )
  }
}
