$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repo = Split-Path $PSScriptRoot -Parent
$releaseConfig = [IO.File]::ReadAllText((Join-Path $repo 'app/build.gradle.kts'))
$releaseVersion = [regex]::Match($releaseConfig, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
$releaseCode = [regex]::Match($releaseConfig, 'versionCode\s*=\s*(\d+)').Groups[1].Value
$pack = Join-Path $repo ('release/v' + $releaseVersion)
foreach ($folder in @('text','graphics','existing-screenshots','verification')) {
    New-Item -ItemType Directory -Force (Join-Path $pack $folder) | Out-Null
}
$notes = @'
Vern 2.6.2 refreshes About and Accessibility, fixes themed text, and adds App settings to Notes. Choose 20 icon palettes or follow your theme, with matching startup colours and motion previews. Enjoy Funky cards across more screens, rounded cover-matched Listen artwork and collapsible/full-screen reading. Page curl, touch gestures and tab swiping are refined, Classics archive links work again, and the wordmark, player and navigation are more compact.
'@
$notes = $notes.Trim()
if ($notes.Length -gt 500) { throw 'Release notes exceed 500 characters' }
[IO.File]::WriteAllText((Join-Path $pack 'text/release-notes-en-US.txt'), $notes)
[IO.File]::WriteAllText((Join-Path $pack 'text/play-console-paste.txt'), "<en-US>`n$notes`n</en-US>")
$title = 'Vern'
$short = 'Read, listen and take notes with your books and documents.'
$full = @'
Bring reading, listening and study together with Vern.

YOUR LIBRARY
Import supported books and documents, organize your library, and pick up where you left off. Explore the classics catalog for your next read.

READ OR LISTEN
Read on screen or use text-to-speech with supported Android and downloadable voices. Adjust playback and follow along with highlighted text. Voice availability and expression depend on the selected engine.

NOTES AND STUDY
Keep highlights and annotations alongside your reading. Create and share notes, and organize study materials in Study Hub.

BUILD YOUR READING HABIT
Set a daily goal and review recorded reading time, weekly activity and reading insights. The home screen brings your current book and progress together.

MAKE IT YOURS
Choose light or dark themes, icon palettes and startup motion. Try an optional Funky cards layout and curved paper page turns. Collapsible Listen controls make more room for reading. Reduced-motion options keep the interface comfortable.

Optional online and AI features require an internet connection and their relevant setup. Download voice models before using them offline.
'@
if ($title.Length -gt 30 -or $short.Length -gt 80 -or $full.Length -gt 4000) { throw 'Listing text exceeds a Play limit' }
[IO.File]::WriteAllText((Join-Path $pack 'text/app-title.txt'), $title)
[IO.File]::WriteAllText((Join-Path $pack 'text/short-description.txt'), $short)
[IO.File]::WriteAllText((Join-Path $pack 'text/full-description.txt'), $full)
Copy-Item (Join-Path $repo 'docs/playstore_icon_512.png') (Join-Path $pack 'graphics/app-icon-512.png')
$banner = [Drawing.Bitmap]::new(1024,500,[Drawing.Imaging.PixelFormat]::Format24bppRgb)
$g = [Drawing.Graphics]::FromImage($banner)
$g.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$g.Clear([Drawing.ColorTranslator]::FromHtml('#4540D8'))
$cream = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml('#FFF6E6'))
$muted = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml('#DCD9FF'))
$pen = [Drawing.Pen]::new([Drawing.Color]::FromArgb(28,255,246,230),2)
for ($i=0; $i -lt 5; $i++) { $g.DrawLine($pen, [single](645+$i*48), [single]500, [single](720+$i*58), [single]0) }
$font = [Drawing.Font]::new('Segoe UI',76,[Drawing.FontStyle]::Bold,[Drawing.GraphicsUnit]::Pixel)
$small = [Drawing.Font]::new('Segoe UI',26,[Drawing.FontStyle]::Regular,[Drawing.GraphicsUnit]::Pixel)
$g.DrawString('Vern',$font,$cream,[single]88,[single]145)
$g.DrawString('Read. Listen. Make it yours.',$small,$muted,[single]94,[single]251)
$mark = [Drawing.Image]::FromFile((Join-Path $repo 'docs/brand/icon-mode-previews/image-1-cream-transparent.png'))
$g.DrawImage($mark, [Drawing.Rectangle]::new(593,70,365,365))
$banner.Save((Join-Path $pack 'graphics/feature-graphic-1024x500.png'),[Drawing.Imaging.ImageFormat]::Png)
$mark.Dispose(); $g.Dispose(); $banner.Dispose(); $cream.Dispose(); $muted.Dispose(); $pen.Dispose(); $font.Dispose(); $small.Dispose()
Copy-Item (Join-Path $pack 'graphics/feature-graphic-1024x500.png') (Join-Path $repo 'docs/playstore_feature_graphic_1024x500.png')
foreach ($name in @('current_reader','pdf_viewer','audio_player','insights')) {
    Copy-Item (Join-Path $repo "docs/playstore_screenshots/$name.png") (Join-Path $pack "existing-screenshots/$name.png")
}
$readme = @"
# Vern Play release pack - v$releaseVersion

## Upload-ready assets
- text/play-console-paste.txt: tagged en-US release notes; $($notes.Length) / 500 Unicode characters (tags excluded).
- text/release-notes-en-US.txt: plain release notes.
- graphics/app-icon-512.png: new big-head cream V on indigo, 512 x 512 PNG, square with no baked-in corner mask.
- graphics/feature-graphic-1024x500.png: 1024 x 500 RGB PNG, no alpha.
- text/: title ($($title.Length)/30), short description ($($short.Length)/80), full description ($($full.Length)/4000).

## Supporting material
- existing-screenshots/: four carried-forward screenshots from the project's existing store set. These are not new captures of this build; review them against the final app before uploading. New home/navigation fixture captures are under verification, not store screenshots.
- verification/: Samsung debug UI captures, focused phone test results and release verification. These are verification fixtures, not store screenshots.
- builds/: signed app bundle and universal/ARM64/32-bit release APKs.
- symbols/: exact-build APK and bundle R8 mappings and available native symbols.

## Release handoff
Source configuration is versionName $releaseVersion, versionCode $releaseCode. This script prepares listing assets only. When release builds are authorized and complete, place the signed bundle/APKs under builds/ and retain verification, SHA-256 hashes and separate exact-build APK/bundle mappings. Before uploading, compare versionCode $releaseCode with the latest uploaded Play build; Play requires a higher code for a new upload. No publication is performed by this script. Local APKs use the upload key, which differs from the Play app signing key; use Play-signed APKs for cross-source compatibility and run tools/check_apk_distribution.ps1. See docs/UPDATE_COMPATIBILITY.md.
Review the Play Console support contact, privacy-policy URL, content rating and Data safety answers against this release. The policy uses myreader.veritas@gmail.com and discloses optional online features and ML Kit SDK diagnostics. No account declarations or rollout settings have been submitted.

## Official specifications checked
- Release notes: https://support.google.com/googleplay/android-developer/answer/9859348
- Store assets: https://support.google.com/googleplay/android-developer/answer/9866151
- Icon: https://developer.android.com/distribute/google-play/resources/icon-design-specifications
"@
[IO.File]::WriteAllText((Join-Path $pack 'README.md'),$readme)
$validation = foreach ($file in Get-ChildItem (Join-Path $pack 'graphics') -Filter *.png) {
    $img = [Drawing.Image]::FromFile($file.FullName)
    [PSCustomObject]@{File=$file.Name;Width=$img.Width;Height=$img.Height;Bytes=$file.Length;PixelFormat=$img.PixelFormat.ToString()}
    $img.Dispose()
}
$validation | ConvertTo-Json | Set-Content (Join-Path $pack 'asset-validation.json')
Write-Output "Release notes: $($notes.Length)/500 characters. Release pack prepared at $pack"
