# ZipSlide

ZipSlide is a high-performance Android application built for browsing `.zip` archives containing slideshow frames, automatically displaying `1.jpg` inside each zip as the visual cover thumbnail.

## Library Rule: only `1.x` slideshows

ZipSlide is a slideshow player, not a file manager, so by default the browser lists **only archives
whose cover resolves through the strict `1.x` rule** (`1.jpg`, `1.jpeg`, `1.png`, `1.webp`, `1.gif`,
or any image whose basename starts with `1.`). Ordinary zips that merely happen to contain images are
hidden, and the header shows how many were withheld.

Turn the rule off at any time from the browser overflow menu ("Showing only 1.x slideshows") or from
*Settings → Library → Only 1.x slideshows*. With the rule off, the ⚠ *No 1.jpg* filter chip returns so
you can audit archives that fell back to natural-sort covers.

## The `1.jpg` Rule & Customization

The core premise of ZipSlide is that every slideshow zip contains `1.jpg` as its first frame. ZipSlide resolves the thumbnail in the following order:

1. Entry with basename exactly `1.jpg` (case-insensitive), preferring root, then shallowest directory depth.
2. `1.jpeg`
3. `1.png`
4. `1.webp`
5. `1.gif`
6. Any image entry whose basename starts with `1.` (e.g. `1.bmp`, `1.heic`) — note that
   `1_title.jpg` does **not** qualify, because the character after `1` is not a dot.
7. Fallback: natural-sorted first image entry. This is the only case that counts as *not* having a
   cover, so such archives are hidden while the `1.x` library rule is on.

### How to Change the Cover Filename Rule
The whole contract lives in `CoverRule` (`app/src/main/java/com/example/util/CoverRule.kt`): edit
`EXACT_PRIORITY` to prefer `cover.jpg` or `poster.png`, and the browser filter, thumbnail pipeline
and unit tests all follow automatically. `CoverRuleTest` covers the ranking, the depth tie-break,
the `__MACOSX` junk filter and the non-strict fallback.

## The Viewer

The viewer is built around reading, not transport controls.

- **Edge taps** — the right third of the frame advances, the left third goes back, the centre toggles
  chrome. A soft directional flash plus a haptic tick confirms every tap. (Toggle in Settings.)
- **Volume keys** — volume **down** moves forward, volume **up** moves back, and *holding* either key
  auto-repeats with the animation skipped, so you can cross a 300-frame archive in a couple of
  seconds. The keys are captured only while the viewer is open; everywhere else they control volume
  as usual, and the pair can be inverted. (Settings → Slideshow → Volume keys navigate.)
- **Remaining-time line** — a 3 dp rail pinned to the very top of the screen shrinks linearly as the
  current frame runs out, and dims the moment playback pauses.
- **23 transitions** with a live, looping preview in Settings — Slide, Dissolve, Glide, Parallax,
  Zoom in, Zoom out, Depth, Cube in, Cube out, Flip, Flip up, Rotate up, Rotate down, Stack, Fan,
  Gate, Accordion, Tablet, Pull back, Push forward, Vertical, Shutter, Carousel, plus **Surprise me**
  which draws a fresh style for every frame. Each is driven by the live pager offset, so a manual
  swipe and an automatic advance share one curve. Length is adjustable (160–1200 ms).
- **Ken Burns drift** — an almost imperceptible 5.5 % zoom across each frame's dwell time.
- **Clean letterboxing** — a fitted frame sits on pure black; nothing moves behind it.
- **Rotation lock** — optionally freeze the orientation for as long as the viewer is open.
- **Cinema mode** — system bars hide with the chrome and return with it.
- **Gestures** — pinch/double-tap zoom with bounded panning, swipe down to dismiss with a live
  scale-and-slide, long-press to save the frame to `Pictures/ZipSlide`.
- **Scrubber** — drag the hairline rail at the bottom to jump anywhere in a 300-frame archive instantly.
- **Resume** — the last settled frame is persisted per archive and restored on the next launch; the
  browser draws a gold progress rail across any card you are part-way through.

### Playback performance

- Frames decode on a bounded `Dispatchers.Default` pool — never on the main thread.
- A dedicated LRU frame cache (¼ of heap) holds decoded frames; the current page plus two in each
  direction stay warm, everything else is evicted.
- Downsampling stops one power-of-two step *before* the target size, so frames are never decoded
  smaller than the panel and then upscaled.
- The open `ZipFile` is reused for the whole session, and all decoded frames are released when playback ends.

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
