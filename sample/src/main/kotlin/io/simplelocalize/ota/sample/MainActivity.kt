package io.simplelocalize.ota.sample

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.simplelocalize.ota.SimpleLocalize

class MainActivity : ComponentActivity() {

  /** Makes stringResource() / getString() resolve through Translation Hosting first. */
  override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(SimpleLocalize.wrapContext(newBase))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          HomeScreen()
        }
      }
    }
  }
}

@Composable
private fun HomeScreen() {
  // Rebuilds the screen when new translations arrive.
  var revision by remember { mutableIntStateOf(SimpleLocalize.revision) }
  DisposableEffect(Unit) {
    val listener = SimpleLocalize.OnTranslationsChangedListener { revision = SimpleLocalize.revision }
    SimpleLocalize.addOnTranslationsChangedListener(listener)
    onDispose { SimpleLocalize.removeOnTranslationsChangedListener(listener) }
  }

  key(revision) {
    Column(
      modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Text(
        text = stringResource(R.string.home_title),
        style = MaterialTheme.typography.headlineMedium
      )
      Text(
        text = stringResource(R.string.home_subtitle),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Card(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = stringResource(R.string.home_greeting),
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.padding(20.dp)
        )
      }

      Text(
        text = stringResource(R.string.home_language),
        style = MaterialTheme.typography.labelLarge
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("en", "pl", "de").forEach { language ->
          FilterChip(
            selected = SimpleLocalize.currentLanguage == language,
            onClick = { SimpleLocalize.setLanguage(language) },
            label = { Text(language) }
          )
        }
      }

      Button(onClick = { SimpleLocalize.refresh() }) {
        Text(stringResource(R.string.home_cta))
      }

      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.Start
      ) {
        Text(
          text = status(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

private fun status(): String = when {
  PROJECT_TOKEN == "YOUR_PROJECT_TOKEN" ->
    "No project token set - showing strings bundled in the app. Add yours in SampleApplication.kt."
  SimpleLocalize.currentLanguage == null ->
    "Downloading translations..."
  else ->
    "${SimpleLocalize.allTranslations().size} keys downloaded, language: ${SimpleLocalize.currentLanguage}"
}
