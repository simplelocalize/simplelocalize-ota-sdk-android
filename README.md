# SimpleLocalize OTA SDK for Android

Over-the-air translations for Android apps. Translations are fetched at runtime from
[SimpleLocalize Translation Hosting](https://simplelocalize.io) (`cdn.simplelocalize.io`), so
fixing a typo or adding a language is a **publish**, not a Play Store release.

- Zero runtime dependencies (no OkHttp, no coroutines, no AndroidX), `minSdk 21`.
- Works with existing `getString(R.string.key)` / XML layouts / Compose `stringResource`
  (opt-in), or through an explicit API.
- Offline first: the last downloaded content is cached in `filesDir`, `res/values*/strings.xml`
  stays the final fallback.
- Cheap refresh: conditional `If-None-Match` requests, so an unchanged refresh transfers no body.

## Installation

```kotlin
dependencies {
  implementation("io.simplelocalize:simplelocalize-ota:0.1.0")
}
```

## Quick start

```kotlin
class MyApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    SimpleLocalize.start(
      this,
      SimpleLocalizeConfiguration(
        projectToken = "5a5b1f...",   // Settings -> Credentials, public by design
        environment = "_production",  // "_latest" for debug builds
        fallbackLanguage = "en"
      )
    )
  }
}
```

Read strings:

```kotlin
textView.text = SimpleLocalize.getStringOrDefault("home.title", getString(R.string.home_title))
```

`start()` is non-blocking: the disk cache is read synchronously (so the first frame already shows
the last known translations) and the network refresh happens on a background thread.

## Zero-touch integration with getString(R.string.…)

```kotlin
class BaseActivity : AppCompatActivity() {
  override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(SimpleLocalize.wrapContext(newBase))
  }
}
```

The wrapped `Resources` translate a resource id to its entry name (`R.string.home_title` ->
`home_title`) and look that key up over the air first, falling back to the strings compiled into
the APK. Use the same resource entry names as translation keys in SimpleLocalize and no call site
changes are needed - XML layouts and Compose `stringResource` go through the same `Resources`.

Views already on screen are not redrawn automatically; listen for changes:

```kotlin
SimpleLocalize.addOnTranslationsChangedListener { recreate() }
```

Compose:

```kotlin
@Composable
fun rememberTranslationsRevision(): Int {
  var revision by remember { mutableIntStateOf(SimpleLocalize.revision) }
  DisposableEffect(Unit) {
    val listener = SimpleLocalize.OnTranslationsChangedListener { revision = SimpleLocalize.revision }
    SimpleLocalize.addOnTranslationsChangedListener(listener)
    onDispose { SimpleLocalize.removeOnTranslationsChangedListener(listener) }
  }
  return revision
}

@Composable
fun Title() {
  val revision = rememberTranslationsRevision()
  key(revision) { Text(stringResource(R.string.home_title)) }
}
```

## Configuration

| Option | Default | Meaning |
|---|---|---|
| `projectToken` | – | Settings -> Credentials |
| `environment` | `_production` | `_latest`, `_production` or a custom environment key |
| `baseUrl` | `https://cdn.simplelocalize.io` | change it for a custom hosting provider (S3/GCS/Azure) |
| `namespaces` | `[]` | downloaded namespaces; passed as the `namespace` argument of `getString` |
| `language` | `null` | forced language key; `null` resolves it from the device locales |
| `fallbackLanguage` | `null` | language used for keys missing in the current one |
| `customerId` | `null` | customer specific translations (`{language}_{customerId}`) |
| `minimumRefreshIntervalMillis` | `600_000` | throttle for automatic refreshes |
| `refreshOnForeground` | `true` | refresh when an activity is resumed |
| `logger` | `null` | diagnostics sink |

## How lookup works

1. over-the-air translation in the current language,
2. over-the-air translation in `fallbackLanguage`,
3. `res/values*/strings.xml` compiled into the APK (when using `wrapContext`),
4. the key / resource default.

Language keys are matched against device locales in this order: `en_GB`, `en-GB`, `en` - the first
one published on the CDN wins and is remembered across launches.

Hosting can publish flat (`{"home.title": "Hi"}`) or nested (`{"home": {"title": "Hi"}}`) JSON;
both are supported and nested payloads are flattened to dot separated keys.

## Refresh model

- `start()` - disk cache immediately, network refresh in the background.
- activity resumed - throttled by `minimumRefreshIntervalMillis`.
- `SimpleLocalize.refresh()` - manual, ignores the throttle.

`_production` is served with `Cache-Control: max-age=3600`, so a publication reaches users within
about an hour; point debug builds at `_latest` to iterate faster.

## Not covered (on purpose)

- Plurals (`getQuantityString`) and string arrays are not overridden - they keep using the
  bundled resources.
- No writes: the SDK never sends anything to SimpleLocalize; the CDN is public and read-only.

## Development

```bash
gradle :simplelocalize-ota:testDebugUnitTest   # JVM tests, incl. a real HTTP server with ETag revalidation
gradle :simplelocalize-ota:assembleRelease
```
