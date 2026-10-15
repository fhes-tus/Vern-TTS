# Supplied icon comparison — 13 October 2026

Compared C:/Users/wwwfe/Downloads/veritas_icon.zip with the initial 56 dp launcher resources. No replacement was applied. The user subsequently selected 52 dp for the current adaptive foreground, monochrome and splash mark; its verified maximum radius is 29.195 dp. The comparisons below describe the initial 56 dp revision.

- Same indigo #4540D8 and cream #FFF6E6 identity.
- Supplied launcher mark is about 55.25 x 42.46 dp, versus current approximately 56 x 43 dp. Its polygon vertices have maximum radius 31.0002 dp versus current flattened curves at 31.44 dp; both fit the 33 dp safe-circle radius.
- Supplied vector paths use straight-line polygon segments. Current paths use cubic curves, retaining smoother outlines when enlarged.
- Both sets include adaptive foreground/background and monochrome layers. Black in the supplied monochrome layer is valid because Android tints the silhouette.
- Supplied legacy PNGs have correct 48/72/96/144/192 pixel sizes. They add compatibility completeness, but current minimum SDK 28 does not need Android 7 fallbacks.
- Supplied Play PNG is 512 x 512, 18,586 bytes, full square with opaque corners. PNG IHDR uses bit depth 8 and color type 2 (24-bit RGB), rather than the required 32-bit RGBA. Current Play PNG uses color type 6 (32-bit RGBA).
- Supplied Play mark is visibly smaller, about half the square width. Current mark occupies about 77%, giving a bolder store thumbnail.
- Supplied ZIP has no notification or splash resources; existing app-specific versions remain necessary.

Recommendation: retain current curves and store artwork. The supplied pack is a useful complete launcher export, but does not provide a clear visual upgrade and its Play PNG requires RGBA conversion before use.
