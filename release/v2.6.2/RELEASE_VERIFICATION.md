# Vern 2.6.2 release verification — 15 October 2026

App source: `4bdcb1d`. Package `com.veritas.reader`, version **2.6.2 / 42**, minimum Android 9 / API 28, target API 36. Later milestone commits contain handoff documentation and evidence only.

## Final artifacts

The final signed AAB passed the offline release build, including R8 and resource shrinking: **BUILD SUCCESSFUL, 18m 5s**. The local universal APK was generated from that exact AAB using official bundletool **1.18.3**, signed with the existing upload/legacy key. It supports ARM64 and 32-bit ARM. Both use the same R8 mapping, whose contents were checked against the mapping embedded in the AAB.

| Artifact | Bytes | Location |
| --- | ---: | --- |
| Upload AAB | 58,963,017 | `builds/upload/Vern-v2.6.2-upload.aab` |
| Local universal APK | 61,845,543 | `builds/local-apks/Vern-v2.6.2-universal-release.apk` |

Exact SHA-256 hashes, signing fingerprints and mapping hash are recorded in `release-manifest.json` and `SHA256SUMS.txt`.

## Checks

- Release vital lint: **No issues found**. This is the vital subset; a full lint run was not completed.
- AAB: bundletool validation exit 0; package/version/SDK/ABI checks pass. All **2,361** content file entries passed Java JAR signature verification with the expected upload certificate; no duplicate ZIP entry names.
- APK: apksigner verification passes; package/version checks, baseline-profile presence, 16 KiB ZIP alignment and ARM64 ELF load-segment alignment pass. Structural alignment checks do not replace testing on a 16 KiB device.
- Earlier security snapshot: **480 JVM tests, zero failures/errors**; Samsung **four security boundary tests and one real playback/native-selection regression** passed. The final app delta consists of UI/help copy corrections, including the last Funky cards subtitle; it does not change those tested security or gesture paths.
- Final release phone startup and About checks: see `verification/phone-release.txt`. Installation used `adb install -r` over the main Vern app, preserving existing data and requiring no uninstall.
- Worker and Python bot suites: **three tests each passed** after privacy-copy corrections. Final live bot/site probes are in `../../docs/verification/2026-10-15/published-milestone.json` (repository-relative path: `docs/verification/2026-10-15/published-milestone.json`).

The build emitted a Compose stack-trace mapping warning for the Glance widget. Assembly and signing succeeded; some generated widget stack-trace detail may be unavailable during crash diagnosis. The original warning is retained in the build log.

## Integration and limits

The source was merged into the original checkout and pushed to GitHub. The website and authenticated Cloudflare bot are deployed. Old plans, one-off mutation scripts, obsolete builds and merge backups were moved into the recoverable milestone archive. The original dirty checkout is also preserved in recovery stash `5439cff5e753d5b7dd81ab8bf59f441507c6042a`.

No Play upload, rollout, public GitHub app release or Telegram announcement was performed. Future public APK downloads should use the Play-signed APK obtained from Console, as described in `docs/UPDATE_COMPATIBILITY.md`. Local upload-key APKs remain incompatible with the different Play app signing identity when switching installation sources.

Before publication, review Data safety for [ML Kit diagnostics](https://developers.google.com/ml-kit/android-data-disclosure) and the app's optional online integrations, verify the highest uploaded version code, and review the required permission declarations. No Console declarations were submitted. Broader tablet/foldable acceptance, Office fidelity, real-model listening/export and full penetration testing remain scoped limitations in `KNOWN_LIMITATIONS.md` and `docs/ACCEPTANCE_BACKLOG.md`.
