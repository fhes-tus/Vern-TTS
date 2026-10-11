# Next update: phone checks

The changes compile and focused JVM tests pass. The phone was later reconnected:
35 focused JVM cases and 8 Android Study Hub/Home/Added cases pass. Actual Home,
Added and Study Overview received visual checks in the isolated checks app.
The broader flows below still need their own hands-on acceptance.

1. **Lite sound and downloads.** Use Marcus US at speed/pitch 1.0 with the same
   October reading. Compare live playback and a fresh WAV export to the original.
   Repeat with another Lite voice. Current small model downloads remain the default;
   no voice redownload is required for the runtime parsing correction.
2. **Studio and concurrent downloads.** Download Studio, then at least three distinct
   Lite voices together. Leave the voice screen while they run, then return. Restart
   the app after completion and confirm every voice remains installed and speaks.
   Additional simultaneous requests queue; downloads do not resume after process death.
3. **Incoming Notes shares.** Share short text without editing it; leave Notes and
   reopen it. Repeat for a link, image, document, audio, video and multiple files from
   another app. Confirm attachments open after returning to Notes and restarting.
4. **Outgoing Notes shares.** Share a note containing text and attachments. The target
   should receive usable files where supported, with no internal phone paths in the
   text. Repeat with a missing attachment: remaining text should still be clean.
5. **Vocabulary.** Select a common word and use Web lookup in the regular reader and
   native PDF viewer. Reopen the Study vocabulary and check meaning, context and source.
   Repeat offline: an unavailable definition should be explicitly labeled.
6. **Book copies.** Use Save to phone for a catalog download (TXT), imported EPUB and
   PDF. Open the saved copies outside Vern. Confirm their format and contents match.
   App uninstall removes internal books; only the copy saved outside the app persists.
7. **Visuals.** Check all five onboarding scenes and copy on a small screen. Check
   navigation/player glass against varied book covers, the Notes navigation animation,
   reduced transparency and high contrast. Verify Study Hub's Material streak icons.

Home and weekly selections retain the completed work. The app icon remains unchanged
while Baldrick's redesign is pending. Background hiss still needs the device comparison
above before it can be called fully resolved.
