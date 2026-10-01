# ZipSlide

ZipSlide is a high-performance Android application built for browsing `.zip` archives containing slideshow frames, automatically displaying `1.jpg` inside each zip as the visual cover thumbnail.

## The `1.jpg` Rule & Customization

The core premise of ZipSlide is that every slideshow zip contains `1.jpg` as its first frame. ZipSlide resolves the thumbnail in the following order:

1. Entry with basename exactly `1.jpg` (case-insensitive), preferring root, then shallowest directory depth.
2. `1.jpeg`
3. `1.png`
4. `1.webp`
5. `1.gif`
6. Any image entry whose basename starts with `1.` (e.g. `1_title.jpg`, `1.bmp`)
7. Fallback: Natural-sorted first image entry (with warning badge indicated on the card).

### How to Change the Cover Filename Rule
To change the thumbnail resolution logic (e.g. to prioritize `cover.jpg` or `poster.png`), edit `ZipRepository.resolveCoverEntry` in `app/src/main/java/com/example/data/ZipRepository.kt`. The method cleanly isolates the matching and ranking rules.

## Multi-Volume & Storage Permission Rationale

Slideshow archives are often large and distributed across internal storage, removable SD cards, and USB OTG drives.
- **Direct File API (`MANAGE_EXTERNAL_STORAGE`)**: Required on Android 11+ to rapidly scan and access nested `.zip` archives across all mounted volumes without triggering thousands of individual SAF dialogs.
- **SAF Secondary Read Path (`Storage Access Framework`)**: Some OEM devices (notably Samsung) restrict SD card access under standard File APIs. ZipSlide includes a full `ZipInputStream` SAF backend over `content://` URIs, guaranteeing that SD cards and custom picked directories work seamlessly.

## Build Order & Tech Stack

- **Kotlin 2.2 + Jetpack Compose + Material 3**
- **Room Database**: Instant cold start metadata caching and resume frame tracking
- **DataStore Preferences**: Fast, reactive settings management
- **WorkManager**: Background storage scanning and thumbnail maintenance
- **No Network / Zero Analytics**: Pure on-device local execution

## Build

The repository includes a pinned Gradle Wrapper and a GitHub Actions workflow that builds every pull request and push to `main` or an Arena branch. The workflow installs Android API 36.1, builds a debug APK, runs the local unit tests, and uploads the APK and reports as a workflow artifact.

To build locally, use JDK 17 and an Android SDK containing platform `android-36.1` and Build Tools `36.0.0`:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
