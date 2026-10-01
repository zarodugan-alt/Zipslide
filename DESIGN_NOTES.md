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
