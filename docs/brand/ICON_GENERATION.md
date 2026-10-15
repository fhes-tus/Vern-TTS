# Vern icon resources

## Current launcher: 13 October 2026

The default launcher is the cream V and sound mark on opaque indigo (`#4540D8`). Customization offers 20 palettes and theme-following resolved from the selected family/light-dark mode. Every palette shares the same 52 dp geometry and monochrome silhouette. The book-and-earbuds notes below describe the earlier artwork.

- `AndroidManifest.xml` uses `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`.
- Both adaptive XML resources in `mipmap-anydpi-v26` reference an opaque background, transparent 108 dp vector foreground, and matching white monochrome vector.
- The logo is centered at 52 dp wide. Its maximum radius is 29.20 dp, within the 33 dp radius safe circle. No corner mask or shadow is baked into the layers.
- The animated splash uses a 432 dp canvas with a 108-unit viewport, as specified for animated splash assets. Its visible area is 288 dp; the logo stays inside the central mask throughout the scale animation.
- `ic_stat_veritas.xml` is a 24 dp pure-white silhouette with the small sound slots removed for status-bar readability.
- The Play listing PNG remains 512 x 512, 32-bit RGBA, 8,634 bytes, with opaque square corners and no shadow.
- Android 7 fallback density sets are unnecessary while the app's minimum SDK is 28 (Android 9). Existing bitmap fallbacks remain available.

Run `tools/check_vern_icons.ps1` to check the safe circle and XML layers and regenerate `vern-adaptive-mask-check.png`. The preview covers circle, squircle, and rounded-square masks; Normal launcher and cold-start splash appearance passed a Samsung SM-A175F debug check on 13 October; themed icons and other launchers/devices remain unverified. See `docs/verification/2026-10-13/icon-check/README.md`.

`tools/build_vern_v_brand.ps1` also runs `generate_vern_icon_palettes.ps1` to regenerate palette variants from the default geometry and Kotlin colour definitions. After adding palettes, run `tools/generate_vern_startup_resources.py` to regenerate matching starting-window styles, launcher aliases and activity entry points. Manifest aliases select a matching starting-window theme before the OS draws its splash. Palette changes are queued until all app activities stop, avoiding Samsung closing the active alias while Settings is open. Android 12+ uses one system splash; older supported Android versions use a matching static starting window.

## Earlier book and earbuds artwork

10 October 2026. User selected the second book/earpiece reference and requested implementation with fixed brand colors, including the complete **Vern** wordmark. Built-in imagegen generated the transparent source; no API/CLI fallback was used.

The app uses the cropped, downsampled PNG foreground and a warm ivory adaptive background. The launcher retains proportional safe padding and an Android monochrome companion. The latest Home correction removes its icon and uses large, heavy **Vern** text (32sp, weight 900) in the neutral Library/Notes title color (`onSurface`), rather than violet or the active accent. Midnight (System) is the fresh/unset default; saved explicit theme choices remain intact. The final book artwork uses Midnight Light’s fixed stellar indigo blue (#4338CA), with deep indigo shading and ivory earbuds/pages. This is a fixed brand asset, not a runtime theme tint.

- Source: `vern-book-earbuds-source.png`
- Launcher preview: `vern-launcher-preview.png`
- App mark: `app/src/main/res/drawable-nodpi/vern_book_earbuds_mark.png`
- Legacy/widget artwork: `app/src/main/res/drawable-nodpi/veritas_reader_icon.png`

## Original generation prompt

Use case: logo-brand. Asset type: production Android app icon foreground for Vern, a reading and listening app.
Input image 1 is a design reference: the purple closed book with two wired earbuds, not a literal screenshot to retain.
Create a new refined modern icon of this idea, on a truly transparent background. Only the isolated mark, no background tile, no rounded square, no lettering, no watermark, no shadows outside the object. One bold upright closed book, slightly softened square corners, visible short spine on left and a compact ivory page edge along the bottom. Two recognizable ivory wired earbuds placed diagonally across the purple cover, one upper and one lower. Their two cables form a single graceful short connected curve contained completely within the cover and ending near its lower right corner; no loose cable extending outside the book. Keep earbuds substantial and simple, not detailed product photography.
Style: premium contemporary app mark, clean near-flat dimensional illustration, crisp broad silhouettes, gentle bevel depth from two flat violet tones, not a glossy photoreal product render. Bold memorable geometry, balanced composition. Limit the palette to vivid violet (#7953D2), deeper violet (#4D308C), and warm ivory (#FFF6E6); only subtle edge antialiasing, no extra colored gradients. Use the deeper violet for the spine, inset earbud speaker openings and the small bookmark, vivid violet cover, ivory earbuds and page edge. Keep the lower bookmark short and subordinate.
Square 1024 composition. The complete book mark should occupy about 68% of the square width and height, centered, with generous transparent breathing room around every part for Android adaptive circular and rounded masks. Make it read clearly at 48 pixels. No long cast shadows or decorative glints.

## Final Midnight Light recolor prompt

Built-in imagegen edit mode; no CLI/API fallback. Generated source is retained in the project. Only transparent bounds cropping, proportional downsampling and composition onto the existing ivory launcher background follow generation.

Use case: precise-object-edit. Asset: Vern Android launcher book-and-earbuds mark. Input image is the edit target. Change only the purple/violet hues of the book cover, spine and bookmark to the Midnight Light theme's stellar indigo blue: primary #4338CA (RGB 67,56,202), shaded spine #1E1B4B, highlights lighter shades of that same blue. Preserve the exact existing closed-book silhouette, placement, perspective, ivory earbuds, wire shape, cream pages, bookmark, matte modern finish, scale and transparent background. No text, extra objects, background, gloss or changes to geometry. The broad book-cover midtone must match #4338CA, not the previous warmer violet. Actual alpha transparency.
