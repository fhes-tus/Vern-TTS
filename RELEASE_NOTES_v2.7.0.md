# Vern v2.7.0 (Build 45)

This update makes adding a document, turning a page and sharing a passage easier. It also fixes missing contents entries, gives files without artwork a proper cover, and brings the reader controls into the places where you use them.

## Scan or choose a photo

- Add → Scan / OCR now offers Take photo and Choose photo. Capture a page with the camera or use Android's photo picker, then extract its text on the device.
- Unsupported files, including Android App Bundles, are rejected with a useful message. Failed or corrupt imports leave the library usable, and a rejected batch item does not stop the next book.
- Photo decoding respects orientation and limits image memory. Temporary captures are cleaned up after use or cancellation.

## Read one PDF page at a time

- PDF Page swipe keeps one complete page in focus and turns it with a left or right swipe. The page fits clear of the toolbar and player; zooming, panning and text selection remain available.
- Reading layout expands directly inside Document Tools. Choose Page swipe or Vertical scroll without opening another dialog. Swipe and Fit to Screen now have distinct icons.
- Reading layout and Swipe effect also expand inside extracted-reader tools, with matching icons and saved preferences.

## Share a passage from Listen

- Long-press a sentence in Listen's Read view, then select up to five sentences to share as an image. Selection pauses playback without moving the reading position.
- Preview the image in the app and choose from 14 colours. The card includes the book cover and title, stronger text, and a monochrome app icon with the Vern name.
- Edit the quote source to credit an author, narrator or character. The credit sits right beneath the quote, aligned to the right above the divider.
- Listen text size now saves independently from extracted-text size.

## Covers that belong to your library

- Books and files without artwork get a title-based cover with clear typography, a stable colour and page-inspired artwork.
- The same cover appears in Library and Home, the details preview, Listen, reader artwork, quote images, notifications and reading/player widgets. Existing artwork keeps priority.

## Settings, Study Hub and appearance

- Kept the Display & theme name. Page navigation is immediately below Live preview. Text size, spacing and paper tone stay in the reader's contextual controls.
- Moved Export study guide PDF into Study Hub, with a saved-book picker that works without an active reader.
- Removed Reading lists from reader tools; Library and app Settings retain them.
- Restored Funky cards on Added grids and Study Hub components. Selection and high-contrast layouts remain straight; reduced motion keeps the static layout.
- Replaced awkward early-morning/night greetings with complete phrases.
- Made charcoal, AMOLED, drawer, document-paper and native PDF colours agree. Both quiet and vibrant Home cards follow the selected theme, the preview matches, and the original animated rings remain.

## Text that stays in the book

- Fixed repeated-header cleanup removing real contents and chapter headings, including repeated Introduction entries. Running-text removal is limited to page edges.
- Mixed PDFs can use the existing automatic-OCR option for image-only pages without replacing readable native text. OCR remains optional.

## Release preparation and verification

Source is version **2.7.0 / 45**. This file is release copy, not a publication announcement. The Play, Telegram channel and bot drafts are in [release/v2.7.0](release/v2.7.0/README.md).

Observed verification and platform limits are recorded in [progress.md](progress.md) and the [Android audit](docs/verification/2026-10-28/android-import-and-console-audit.md). The exact supplied PDF was checked on Samsung Android 16; its current reading was backed up and repaired without replacing page 6 onward. A new signed AAB, exact-build mapping, checksums and Play analysis are still required for distribution. No new release was uploaded, no Telegram post was sent, and no bot was deployed by this preparation.
