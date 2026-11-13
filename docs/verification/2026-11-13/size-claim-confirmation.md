# Verification of proposed Android size reductions

13 November 2026. Read-only application inspection on `test/reader-ui-update`; no Gradle build, test, installation or application-source change was performed. Evidence is under ignored `.artifacts/size-confirmation-20261113/`.

## Material icons

Confirmed dependency: `androidx.compose.material:material-icons-extended`, cached Android version 1.7.8. The AAR is **35,720,998 bytes**; its nested `classes.jar` is **37,425,981 bytes (35.69 MiB)** and contains **11,105 classes**. A JAR/AAR length is not an APK reduction.

All 11,105 classes occur in the current debug APK (174,778,078 bytes; SHA-256 `43a211d19a75ec451811ecdbca31f05eef8cab17b32856f50de798ac4ff7264f`). Their DEX instructions total 11,819,288 bytes; this excludes class/string/annotation/debug structures and is not their complete DEX size or projected removable size. No exact 35–40 MB APK saving was measured.

Production Kotlin source scanning found **226 distinct direct icon variants / 180 icon names**, including **189 variants belonging to extended**. Explicit imports name 222 variants; six files use wildcard icon imports. These are source-scan counts, not a compiler-resolved final extraction manifest. The quoted approximately 95 icons is not supported by current source. Evidence: `icon-usage.json`, including the lists and wildcard locations.

The retained release already uses R8. Its two DEX files total only **17,013,564 bytes**. There are no original extended-class descriptors in the optimized DEX; surviving icon paths may be inlined or obfuscated. R8's usage file also records extensive original icon-class elimination. Consequently, extracting the same used vector definitions primarily removes Debug/build input weight; a comparable Release-size reduction is not established. Input dependency size does not itself prove large unused strings remain in a release.

The strategy is reasonable, with original Apache notices, vector paths, style variants, caching and auto-mirroring preserved. It is not automatically risk-free, nor does it prove every build becomes faster. [Android's icon guidance](https://developer.android.com/develop/ui/compose/graphics/images/material) acknowledges build-time costs; [R8 documentation](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization) explains removal and inlining.

## Other claims

| Claim | Verified conclusion |
| --- | --- |
| Native speech saves 25.8 MB per ABI/base download | The retained binaries total **25.77 MiB ARM64 / 17.92 MiB ARMv7**, uncompressed. These are not download-size measurements. On-demand delivery defers that payload for non-neural users; neural users still install it. A custom runtime reaching 6–8 MB is unverified and must support current **Piper, Kokoro and Kitten** models. |
| Bouncy Castle permits removal of approximately 5,000 classes | The blanket keep rule and **6,289 release classes** are confirmed. Their **2,433,710 instruction bytes** describe the entire family, not removable bytes. The three configured 1.84 runtime JARs total **8,909,902 bytes**, rather than the quoted 10.4 MB. Provider registration is reflective; retaining PDFBox security handlers alone does not establish which BC algorithms can be deleted. |
| Screenshots/covers save 1.5–2 MB with no visual loss | The measured pools are **1,896,570 bytes / 29 manual JPEGs** and **793,964 bytes / 39 cover JPEGs**. JPEGs are already compressed. A 1.5–2 MB visually lossless WebP saving has not been demonstrated. Earlier PNG-only recompression evidence is separate and does not establish a JPEG/WebP result. |

Sources for the architectural possibilities: [Play on-demand delivery](https://developer.android.com/guide/playcore/feature-delivery/on-demand), [ONNX Runtime custom builds](https://onnxruntime.ai/docs/build/custom.html). These support the approaches, not Vern-specific projected savings.

Commands actually used: `git branch --show-current`, `git status --short`, scoped `Get-Content` / `Get-ChildItem` / `rg`, .NET ZipArchive inspection of the cached AAR/source JAR, and Node ZIP/DEX/source-reference/R8-usage inventory. The adapted `audit-icons.cjs` retains the prior audit's read-only parser and classifies descriptors against the actual AAR class list. `icon-package-inventory.json` records the current Debug APK and retained 2.8.2 release AAB, whose SHA-256 remains `ff357001130230980c35735c517e4f547b91f4c70cae06e87fdae13564ee1a53`. The release artifact predates subsequent source work.

Observed inspection limits: broad initial output was truncated, so conclusions use the saved focused inventories; an optional guessed `NeuralTtsFactory.kt` path was absent and the actual voice model manager confirmed all three engine families. One official optimization URL was unavailable; its documented enable-optimization page was read instead. No build-time, runtime-speed or before/after APK saving was measured. Existing local changes were preserved.
