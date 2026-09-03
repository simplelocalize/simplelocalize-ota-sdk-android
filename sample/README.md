# Example app

Minimal Android app showing over-the-air translations end to end.

## Run

From the repository root (that is where the Gradle wrapper lives):

```bash
./gradlew :sample:installDebug
```

Or open `ota-sdk-android` in Android Studio and run the `sample` configuration.

It works out of the box with the strings bundled in `res/values/strings.xml`.

## Connect it to your project

1. Put your project token (Settings -> Credentials) in `SampleApplication.kt`.
2. In SimpleLocalize create the keys `home_title`, `home_subtitle`, `home_greeting`,
   `home_cta`, `home_language` - translation keys must match the resource entry names.
3. Publish to `_latest` and tap **Refresh translations** in the app.

## What it shows

- `SimpleLocalize.start()` in `SampleApplication.kt`,
- `SimpleLocalize.wrapContext()` in `MainActivity`, so plain `stringResource(R.string.home_title)`
  resolves over the air,
- a listener that rebuilds the screen when new translations arrive,
- `SimpleLocalize.setLanguage()` behind the language chips.
