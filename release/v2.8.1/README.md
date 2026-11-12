# Vern 2.8.1 / build 48

Release preparation was stopped at the user's request after preparing notes. The 2.8.1 build was cancelled; the artifact paths below are planned outputs, not completed deliverables. No Play upload or rollout has been performed from this chat.

- `builds/Vern-2.8.1-48-upload.aab`: signed bundle for Play Console.
- `builds/Vern-2.8.1-48-legacy.apk`: local upload-key APK for the existing legacy signing channel.
- `symbols/`: exact release R8 mapping.
- `verification/`: build, signing, checksums and focused device-check receipts.
- [Play release notes](PLAY_STORE_RELEASE_NOTES.txt).

Follow the selected distribution route: upload the AAB to Play, obtain that build's Play-signed universal APK, and publish that APK on GitHub. The locally signed APK cannot update Play-signed installations. Do not uninstall an existing app to work around the signature difference. Compare code 48 with the latest Play upload before submitting.

The responsive source work covers Notes, Study, Add/import, Reader/PDF, shared dialogs, settings, navigation and widgets. Samsung API 36 and Infinix API 30 checks provide phone evidence; forced window sizes and synthetic hinges do not establish physical tablet/fold/flip acceptance. See [progress](../../progress.md) for exact results and limits.
