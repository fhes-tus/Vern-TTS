# Compatibility probe for already-downloaded official assets, not Tauri acceptance.
$ErrorActionPreference = 'Stop'
$desktopRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $desktopRoot
$runtime = Join-Path $desktopRoot '.tools/sherpa-onnx-v1.13.8-win-x64-shared-MT-Release/bin/sherpa-onnx-offline-tts.exe'
if (!(Test-Path -LiteralPath $runtime)) { throw 'The upstream compatibility runtime has not been downloaded. See progress.md.' }
$expected = @{
    'vits-piper-en_US-lessac-medium-int8' = 'F1C6D0295CF16087B05F80FDCA5B44DACA5CD78E2C425D419A42BA34929805F9'
    'kokoro-int8-multi-lang-v1_0' = '4C3052ABAA60943A341F193888CF6ABD68787DAE6AB8AE5C925A706CAA247E4E'
}
foreach ($name in $expected.Keys) {
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath ".tools/$name.tar.bz2").Hash
    if ($actual -ne $expected[$name]) { throw "Archive hash mismatch: $name" }
}
New-Item -ItemType Directory -Force .artifacts | Out-Null
& $runtime '--vits-model=.tools/vits-piper-en_US-lessac-medium-int8/en_US-lessac-medium.onnx' '--vits-tokens=.tools/vits-piper-en_US-lessac-medium-int8/tokens.txt' '--vits-data-dir=.tools/vits-piper-en_US-lessac-medium-int8/espeak-ng-data' '--num-threads=2' '--output-filename=.artifacts/lite-upstream.wav' 'Welcome to Vern. Your next chapter is waiting.'
if ($LASTEXITCODE -ne 0) { throw "Piper failed: $LASTEXITCODE" }
& $runtime '--kokoro-model=.tools/kokoro-int8-multi-lang-v1_0/model.int8.onnx' '--kokoro-voices=.tools/kokoro-int8-multi-lang-v1_0/voices.bin' '--kokoro-tokens=.tools/kokoro-int8-multi-lang-v1_0/tokens.txt' '--kokoro-data-dir=.tools/kokoro-int8-multi-lang-v1_0/espeak-ng-data' '--kokoro-lexicon=.tools/kokoro-int8-multi-lang-v1_0/lexicon-us-en.txt' '--kokoro-lang=en-US' '--sid=3' '--num-threads=2' '--output-filename=.artifacts/studio-upstream.wav' 'Welcome to Vern. Your next chapter is waiting.'
if ($LASTEXITCODE -ne 0) { throw "Kokoro failed: $LASTEXITCODE" }
Write-Output 'Both official models produced WAV files. This does not verify the Rust/Tauri integration.'
