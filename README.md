# FixIt

Android repair marketplace (UNAJ, "Aplicaciones Moviles", TP2). Clients post repair requests and nearby professionals accept them (first one wins).

Kotlin + Jetpack Compose, Clean Architecture, multi-module Gradle (`:app`, `:core:domain`, `:core:data`, `:core:ui`, `:features:auth|client|professional`).

## Prerequisites

- **JDK 17 or 21 to run Gradle.** The system `java` may be an older JDK (for example JDK 8 on `PATH`), which cannot run AGP 9. Use the JBR bundled with Android Studio.
- **Android SDK** with *Android SDK Platform 37.2* (API 37, minor 2) and *Build-Tools* installed through the SDK Manager.
- **`local.properties`** in the repo root pointing to the SDK. It is git-ignored and must never be committed:

  ```properties
  sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
  ```

### Cloudinary keys (photo uploads, optional)

Photo uploads use an unsigned Cloudinary upload preset. Add both keys to `local.properties` (never commit them):

```properties
CLOUDINARY_CLOUD_NAME=<your cloud name>
CLOUDINARY_UPLOAD_PRESET=<your unsigned preset name>
```

`:core:data` reads them into `BuildConfig`. When they are missing they become empty strings: the project still compiles and tests pass, but uploading a photo fails with a generic error.

## Recommended local setup (no global configuration)

Point `JAVA_HOME` to Android Studio's JBR **per command**, so no user or global Gradle configuration is touched.

Git Bash:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew test
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```

PowerShell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat test
```

Alternative: set `org.gradle.java.home` in `~/.gradle/gradle.properties` (not versioned) to make it permanent for every Gradle project on the machine.

## Notes

- The project path may contain non-ASCII characters (`tercerAño`). `gradle.properties` sets `android.overridePathCheck=true` so AGP does not abort on Windows.
- Gradle wrapper: 9.7.1 (same as the course reference repository, `mauromarod/unaj-am`).
- Tests: JUnit4 + `kotlinx-coroutines-test`, hand-written fakes, no mocking library. Run them with `./gradlew test`.
