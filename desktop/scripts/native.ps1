param([Parameter(ValueFromRemainingArguments=$true)][string[]]$TaskArgs)
$ErrorActionPreference = 'Stop'
$desktopRoot = Split-Path $PSScriptRoot -Parent
$localCargo = Join-Path $desktopRoot '.tools/cargo'
$localRustup = Join-Path $desktopRoot '.tools/rustup'
if (Test-Path -LiteralPath (Join-Path $localCargo 'bin/rustup.exe')) {
    $env:CARGO_HOME = $localCargo
    $env:RUSTUP_HOME = $localRustup
    $env:PATH = "$(Join-Path $localCargo 'bin');$env:PATH"
}
Set-Location -LiteralPath $desktopRoot
if (!$TaskArgs.Count) { $TaskArgs = @('tauri','dev') }
if ($TaskArgs[0] -eq 'cargo') {
    $cargoArgs = @($TaskArgs | Select-Object -Skip 1)
    & cargo @cargoArgs
} elseif ($TaskArgs[0] -eq 'tauri') {
    $tauriArgs = @($TaskArgs | Select-Object -Skip 1)
    & (Join-Path $desktopRoot 'node_modules/.bin/tauri.cmd') @tauriArgs
} else {
    & npm @TaskArgs
}
exit $LASTEXITCODE
