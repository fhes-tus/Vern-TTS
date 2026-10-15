# Release size review — 9 October 2026

Local builds for size analysis only. Nothing was published or installed. The baseline
and final measurement are from this worktree's update, not a comparison with an older
store release. Both use the existing release shrinking and ABI split configuration.

## Measured result

| APK | Before (MiB) | After (MiB) | Saved (MiB) | Reduction |
| --- | ---: | ---: | ---: | ---: |
| ARM64 | 55.28 | 51.26 | 4.02 | 7.27% |
| ARMv7 | 46.33 | 43.40 | 2.93 | 6.33% |
| Universal, both ABIs | 76.16 | 69.21 | 6.95 | 9.13% |

Exact before → after bytes: ARM64 57,960,884 → 53,745,524; ARMv7
48,582,226 → 45,504,886; universal 79,859,696 → 72,571,500.

The app uses Sherpa's JNI entry point. Excluded unused `libsherpa-onnx-c-api.so`
and `libsherpa-onnx-cxx-api.so` from APK packaging, preserving both CPU architectures
and the JNI/ONNX Runtime pair. The bundled upstream README explicitly confirms these
two extra entry points are unnecessary for Android JNI apps; ELF dependencies agree.

The numbers above also include the subsequent runtime upgrade to Sherpa 1.13.8 for
the Piper phoneme framing fix. Before that upgrade, the packaging change alone measured
50.65 MiB ARM64, 43.03 MiB ARMv7 and 68.24 MiB universal. The newer runtime adds a little
size while preserving the overall reduction. Voice model URLs and download sizes are
unchanged; downloadable models are separate from the APK.

## Remaining opportunities

The final ARM64 APK's stored payload is approximately:

| Component | MiB |
| --- | ---: |
| Native libraries | 25.78 |
| DEX code | 18.58 |
| Android resources | 4.10 |
| Assets | 2.34 |
| Other | 0.16 |

Further size investigation should focus on native runtime and code packaging rather
than removing small UI assets or voice features. A custom inference runtime would need
an operator inventory covering all supported Piper/Kokoro models before it could be
considered compatible.

A local DEFLATE calculation on the two DEX entries gives 8,255,484 bytes versus
19,486,448 bytes currently stored: **10.71 MiB potential payload reduction**. This is
an estimate, not a rebuilt or install-tested APK. Android Gradle's
[`DexPackaging.useLegacyPackaging`](https://developer.android.com/reference/tools/gradle-api/9.1/com/android/build/api/dsl/DexPackaging)
controls DEX compression. Leave production packaging unchanged until installation,
startup and storage behavior are measured on a phone. No additional experimental
release build or public release was made for this estimate.

## Verification and limits

- Measurement build passed; fourteen focused voice/reader JVM tests passed.
- All three APKs pass signature verification and 16 KiB ZIP alignment checks.
- ZIP inspection confirms required JNI/ORT libraries for each ABI and the absence
  of both unused entry points. Native library provenance and hashes are recorded in
  `app/src/main/jniLibs/SHERPA_RUNTIME.md`.
- The measurement build compiled before the final cosmetic Reading Insights restoration
  and glass-shadow correction. Final production and device-test Kotlin compilation
  passed afterwards; no extra APK was assembled for those cosmetic changes. Their
  native packaging configuration is identical to the measured build.
- No Android device was connected. Use `docs/NEXT_UPDATE_DEVICE_CHECKS.md` for the
  remaining voice, share, storage and visual checks. No debug APK was assembled.


## 10 October: isolated debug DEX experiment

The user requested source fixes without another release build. A copy of the unshrunk ARM64 `.checks` debug APK was repacked with DEFLATE DEX entries, 16 KiB ZIP alignment and the project's local test signer. No release artifact or production packaging setting was changed. Only `.checks` was updated; the original debug APK was restored afterward.

| Measurement | Stored DEX | Compressed DEX |
| --- | ---: | ---: |
| Debug APK size | 141.38 MiB | 63.82 MiB |
| Two install times | 16.26 s / 9.16 s | 10.49 s / 10.75 s |
| Median cold-process activity start, six launches | 1554 ms | 1472.5 ms |
| Android reported installed app code | 150,119,936 bytes | 173,078,016 bytes |

The experiment reduces the debug download by 77.57 MiB but increases installed app code by 21.89 MiB. There is no reliable startup advantage: these are six sequential force-stop/start samples on one phone with warm filesystem caches, not a controlled benchmark or a shrunk release comparison. The first candidate used the default Android debug signer and was correctly rejected; the successful comparison used the same local test certificate as the baseline, retaining `.checks` data. Credentials were not printed.

Decision: **keep production DEX packaging unchanged**. The investigation is complete; a production packaging change would require a later controlled release experiment. The user subsequently authorized the normal signed release and generated-file cleanup. Compact evidence is retained at `docs/verification/2026-10-10/measurement.json` and `docs/verification/2026-10-10/dex-packaging-experiment-retry.log`.
