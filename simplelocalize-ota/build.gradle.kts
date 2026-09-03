plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
}

android {
  namespace = "io.simplelocalize.ota"
  compileSdk = 36

  defaultConfig {
    minSdk = 21
    consumerProguardFiles("consumer-rules.pro")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions {
    jvmTarget = "17"
  }

  sourceSets {
    getByName("main").java.srcDirs("src/main/kotlin")
    getByName("test").java.srcDirs("src/test/kotlin")
  }

  testOptions {
    unitTests.isReturnDefaultValues = true
  }
}

dependencies {
  // Real org.json implementation for JVM unit tests; on device the platform one is used.
  testImplementation("org.json:json:20250107")
  testImplementation("junit:junit:4.13.2")
}
