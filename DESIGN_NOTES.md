# ZipSlide Design Notes

## Creative Intent & Visual Identity

ZipSlide was crafted with a dark-first, warm-gold aesthetic designed specifically for viewing photographic slideshows without visual fatigue or generic "system default" styles.

### 1. Warm-Gold & Luxury Charcoal Palette
- **Background (`#0B0B0F`)**: Deepest black-slate providing maximum contrast for images while remaining warmer than OLED pure black on browser cards.
- **Surfaces (`#14141A`, `#1C1C24`, `#26262F`)**: Three calculated tonal elevations that maintain optical depth without requiring heavy elevation shadows.
- **Accent (`#E8B458`) & AccentSoft (`#3A2E18`)**: An understated warm gold tone that guides primary actions, filter chips, and highlights without distracting from photograph covers.
- **No Dynamic Color**: Material You dynamic color is explicitly disabled to preserve the curated, cinematic identity of the brand across all devices and Android versions.

### 2. Geometry & Spacing
- **4:5 Aspect Ratio Cards**: Matches standard photographic proportions (e.g. 8x10, portrait crops), ensuring covers fill cards naturally.
- **12dp Card Gap with 20dp Screen Gutter**: Provides generous visual breathing room and avoids cramped edges.
- **Scaffold Safe Insets**: Edge-to-edge support with strict `WindowInsets` padding for notches and gesture navigation pills.

### 3. Motion & Ergonomics
- **Fast 140ms Press Response**: Scale down to 0.97 on press gives instantaneous physical tactile feedback.
- **Spring Physics for Pager**: Natural deceleration when swiping frames in the slideshow.
- **Zero-Chrome Entry**: When launching a slideshow, chrome is immediately hidden so the viewer is immersed strictly in the photography. Tap toggles chrome with an automatic 3-second fadeout.

### 4. The Viewer Rewrite

The original viewer wore a media-player costume: a floating pill with skip-back, a 56 dp accent
play button and skip-forward. That borrowed vocabulary from audio, where there is nothing to look
at. A slideshow is read like a book, so the controls were deleted and replaced with the page-turn
model: right third forward, left third back, centre reveals chrome. The only persistent affordance
is a 3 dp remaining-time rail at the very top edge — the single piece of information a viewer
actually needs while a frame is on screen.

- **One curve for every advance.** Manual swipes and the interval timer both drive the same pager
  offset, so an auto-advance is indistinguishable from a deliberate swipe. Transitions are computed
  inside `graphicsLayer` blocks, which keeps a 60–120 Hz swipe in the draw phase instead of
  recomposing the frame tree.
- **Never a blank screen.** Frames are warmed two pages ahead and behind, and a fitted image gets a
  dimmed, over-scaled copy of itself as letterbox filler instead of flat black bars.
- **Feedback over chrome.** A directional gradient flash and a light haptic acknowledge each tap, so
  the absence of buttons never feels like an absence of response.
- **Cinema mode.** System bars are tied to chrome visibility: when the controls go, the OS goes.

### 5. Library Honesty

The browser now lists only archives that satisfy the strict `1.x` cover rule, with the hidden count
shown in the header subtitle rather than silently dropped. The rule is reversible from the overflow
menu in one tap, and the "No 1.jpg" audit chip reappears when it is off.
