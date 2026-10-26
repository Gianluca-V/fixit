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

### Push notifications (optional)

Push notifications are sent by a Cloudflare Worker through FCM (`worker/`; setup, secret and deploy steps are in `worker/README.md`). Once the Worker is deployed, add its URL to `local.properties` (never commit it):

```properties
PUSH_WORKER_URL=https://<your-worker>.<your-subdomain>.workers.dev
```

`:core:data` reads it into `BuildConfig`. When it is missing it becomes an empty string: the request repository is not decorated, no request is sent to the Worker and the app works without notifications. Topic subscriptions and the notification permission are still handled, so a device subscribes and can receive pushes as soon as any client with the URL sends them.

### Map and address search (no keys)

"Elegir en el mapa" uses MapLibre with the OpenFreeMap style (`map_style_url` in `core/ui/src/main/res/values/strings.xml`), and address suggestions come from the public Photon instance by komoot. Neither needs an API key, an account or a card. Map data © OpenStreetMap contributors; the map shows the attribution. To work without network, bind `FakeAddressSearch` instead of `PhotonAddressSearch` in `core/data/.../di/NetworkModule.kt`.

### Firebase

Since Phase 2 the app uses Firebase Auth and Firestore (bound in `core/data/.../di/RepositoryModule.kt`; revert only that file to go back to the Phase 1 fakes). `app/google-services.json` is required, git-ignored and shared out of band. Deploying the Firestore rules and indexes and seeding the test users is described in `docs/TEAM.md` (section "Firebase").

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
