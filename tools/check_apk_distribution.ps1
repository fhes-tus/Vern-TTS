param(
    [Parameter(Mandatory=$true)][string]$Apk,
    [Parameter(Mandatory=$true)][string]$ApkSigner,
    [ValidateSet('Play','LegacyApk')][string]$Channel = 'Play'
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$policy = Get-Content -LiteralPath (Join-Path $repo 'release/distribution-signing.json') -Raw | ConvertFrom-Json
$apkPath = (Resolve-Path -LiteralPath $Apk).Path
$signerPath = (Resolve-Path -LiteralPath $ApkSigner).Path
$certificates = & $signerPath verify --print-certs $apkPath 2>&1
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
$actual = @($certificates | ForEach-Object {
    if ($_ -match 'certificate SHA-256 digest:\s*([a-fA-F0-9]{64})') { $Matches[1].ToLowerInvariant() }
} | Select-Object -Unique)
$expected = if ($Channel -eq 'Play') { $policy.playAppSigningSha256 } else { $policy.uploadAndLegacyApkSha256 }
if ($actual.Count -ne 1 -or $actual[0] -ne $expected) {
    throw "This APK is not signed for the $Channel channel. For Play-compatible downloads, use the signed universal APK from Play Console; an AAB upload signature is not the distributed APK signature."
}
Write-Output "Verified $Channel APK signing certificate: $expected"
# Package name, versionCode and rotation lineage also need validation before publication.
