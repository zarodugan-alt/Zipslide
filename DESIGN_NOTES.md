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
- **Never a blank screen.** Frames are warmed two pages ahead and behind.
- **Nothing moves behind the photograph.** An over-scaled, dimmed copy of the frame was tried as
  letterbox filler; during a page turn its parallax read as a second, lagging image rather than as
  depth, so the letterbox is now pure black. The photograph is the only thing in motion.
- **Hands stay still.** Volume down / volume up turn pages without moving a thumb across the glass,
  and auto-repeat drops the animation so holding a key is a scrub, not a queue of animations.
- **Feedback over chrome.** A directional gradient flash and a light haptic acknowledge each tap, so
  the absence of buttons never feels like an absence of response.
- **Cinema mode.** System bars are tied to chrome visibility: when the controls go, the OS goes.

### 5. Library Honesty

The browser now lists only archives that satisfy the strict `1.x` cover rule, with the hidden count
shown in the header subtitle rather than silently dropped. The rule is reversible from the overflow
menu in one tap, and the "No 1.jpg" audit chip reappears when it is off.

### 6. A Transition Catalogue, Not a Toggle

The transition list is a Compose re-implementation of the vocabulary the Android ecosystem already
settled on through years of `ViewPager.PageTransformer` collections — depth, cube in/out, horizontal
and vertical flips, rotate up/down, stack, fan, gate, accordion, tablet, foreground/background,
parallax and the two zooms — rewritten as pure `graphicsLayer` maths in `SlideTransitions.kt`.

Two rules keep it honest:

1. **One implementation.** The Settings preview runs the *same* `applyTransition` function the viewer
   runs, on two stand-in frames, on a loop. There is no mock animation to drift out of sync.
2. **One coordinate contract.** `pageOffset` is Compose's convention; the file converts once to the
   classic `position = -pageOffset` so the well-known transformer formulas port verbatim, and every
   "non-sliding" style pins itself with `translationX = -position * width`.

*Surprise me* resolves to a concrete style on each pager settle — never mid-animation — so a random
run never changes curve halfway through a turn.

## 7. Two Orders, Two Homes

The reported bug — "sorting the library also reorders the pictures inside a zip" — is the kind of
thing that happens when one `AppSettings.sortBy` is read by two subsystems that should never have
shared it. Rather than hunt for a single offending call site and hope, the fix makes the separation
structural:

- `util/ZipSorting.kt` owns the **only** library comparator. The repository and the browser view
  model both delegate to it, so they can no longer drift (they previously had two copies, both
  comparing zero-padded strings — a size over 20 digits or a name longer than the pad sorted wrong).
- `ZipRepository.playbackFrames(zip, order)` owns the **only** frame ordering, and its signature
  simply has nowhere to put a library sort. `FrameOrderTest` walks every library preset and asserts
  the frame list does not move.

The UI mirrors the split: "Library order" and "Frame order" are two separate rows, each with its own
vocabulary. A user who wants reverse reading order now has a named control for it instead of
discovering it as a side effect.

## 8. Why Removing Double-Tap Made Taps Faster

`detectTapGestures` cannot fire `onTap` until it knows a second tap is not coming, so every page
turn paid the full double-tap timeout — about 300 ms of nothing. The gesture was also competing with
the pager for the same pixels. Dropping `onDoubleTap` means a tap dispatches on the up event, and the
zones grew to 42 % per side so "the middle of the right half" is unambiguously forward. Zoom loses
nothing a slideshow needs: pinch still goes to 6×, and pinching back out restores the fit.

## 9. Making a Swipe Feel Like a Swipe

A transition that looks beautiful on a 520 ms timer can feel like rubber under a thumb, because the
finger is driving a curve that was designed to be driven by a clock. The viewer now distinguishes
*who* is scrolling: a `programmaticScroll` flag is raised around `animateScrollToPage`, and any
scroll without it is a drag, which renders with a plain one-to-one push. Styled motion returns the
moment the pager settles — and since every style is the identity transform at offset 0, the handover
is literally invisible. A medium-low snap spring and a 0.22 positional threshold finish the job, with
three frames preloaded in each direction so the decoder is never what you are waiting for.
