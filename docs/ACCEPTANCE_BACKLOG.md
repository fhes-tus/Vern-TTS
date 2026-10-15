# Remaining acceptance — 2.6.2

Updated 15 October 2026. Current behavior and limitations are in `KNOWN_LIMITATIONS.md`; implementation repairs are summarized in `TECHNICAL_DEBT_RECHECK.md`. Local APK/AAB assembly was authorized on 15 October. Publication remains pending.

- Broaden tablet, foldable, landscape, font-scale and Android-version coverage. Samsung checks do not establish all-device support.
- Complete human listening with real neural models: mixed casts, export, rate, pitch, cadence and Lite hiss. Synthetic output and system TTS checks do not replace that acceptance. The previously paused real-model acceptance remains paused until requested.
- Check long reading/listening sessions, process death, focus changes and idle-policy behavior with real books. Exercise long-history responsiveness and widget-host resizing.
- Complete source comparisons for complex Word and PowerPoint documents. Word pagination remains approximate; the broader Office-fidelity extension was excluded from the agreed scope.
- Exercise real recipient Notes/PDF/audio sharing, external saved originals, legacy vocabulary retries and browser handoffs under offline/reconnect conditions. Portable ZIP backup/restore is the selected cross-device Notes route; account-based automatic sync is outside scope.
- Broaden launcher/themed-icon and startup-motion checks beyond Samsung. Test sustained curl performance with large books and every relevant motion/selection configuration.
- Finish release/R8 checks only after release builds are authorized. Validate package, version code, signer/lineage, actual download assets and Play-track eligibility. Use Play-signed APKs for future Play-compatible distribution; see `docs/UPDATE_COMPATIBILITY.md` for the existing-key mismatch and migration limits.
- Complete full lint and a broader dependency/native-binary security review. The security audit is a scoped source and dependency review, not proof that no vulnerability exists. Credentials removed from current bot source remain in Git history; the owner declined rotation.

Verification snapshots are under `docs/verification/`. Superseded implementation plans and mutation scripts were removed during milestone cleanup after their remaining acceptance items were consolidated here. Their local recovery archive and original-tree stash are retained outside the published source.
