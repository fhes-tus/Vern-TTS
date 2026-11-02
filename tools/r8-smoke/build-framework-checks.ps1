param(
    [string] $SdkRoot = "$env:LOCALAPPDATA/Android/Sdk",
    [string] $JavaRoot = 'C:/Program Files/Android/Android Studio/jbr'
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$out = Join-Path $repo '.artifacts/android-release-checks/framework'
$buildTools = Join-Path $SdkRoot 'build-tools/36.1.0'
$androidJar = Join-Path $SdkRoot 'platforms/android-36/android.jar'
$env:JAVA_HOME = $JavaRoot
New-Item -ItemType Directory -Force -Path $out,(Join-Path $out 'classes'),(Join-Path $out 'dex') | Out-Null
function Invoke-Checked([string] $Program, [string[]] $Arguments) {
    Write-Output ([System.IO.Path]::GetFileName($Program))
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program exited $LASTEXITCODE" }
}
Invoke-Checked (Join-Path $JavaRoot 'bin/javac.exe') @('-source','17','-target','17','-classpath',$androidJar,'-d',(Join-Path $out 'classes'),(Join-Path $PSScriptRoot 'framework/ReleaseImportChecks.java'))
Invoke-Checked (Join-Path $JavaRoot 'bin/jar.exe') @('--create','--file',(Join-Path $out 'checks.jar'),'-C',(Join-Path $out 'classes'),'.')
Invoke-Checked (Join-Path $buildTools 'd8.bat') @('--lib',$androidJar,'--min-api','28','--output',(Join-Path $out 'dex'),(Join-Path $out 'checks.jar'))
Invoke-Checked (Join-Path $buildTools 'aapt2.exe') @('link','--manifest',(Join-Path $PSScriptRoot 'framework/AndroidManifest.xml'),'-I',$androidJar,'-o',(Join-Path $out 'unsigned.apk'))
Invoke-Checked (Join-Path $JavaRoot 'bin/jar.exe') @('--update','--file',(Join-Path $out 'unsigned.apk'),'-C',(Join-Path $out 'dex'),'classes.dex')
Invoke-Checked (Join-Path $buildTools 'zipalign.exe') @('-f','4',(Join-Path $out 'unsigned.apk'),(Join-Path $out 'aligned.apk'))
# Read the existing local signing configuration without printing credentials or
# placing passwords on command lines. Only the disposable test APK is signed.
$properties = Get-Content -LiteralPath (Join-Path $repo 'local.properties') -Raw | ConvertFrom-StringData
$env:VERN_CHECK_STORE_PASS = $properties.RELEASE_STORE_PASSWORD
$env:VERN_CHECK_KEY_PASS = $properties.RELEASE_KEY_PASSWORD
try {
    Invoke-Checked (Join-Path $buildTools 'apksigner.bat') @('sign','--ks',(Join-Path $repo 'keystore/veritas-reader-test-release.jks'),'--ks-key-alias','veritasreader','--ks-pass','env:VERN_CHECK_STORE_PASS','--key-pass','env:VERN_CHECK_KEY_PASS','--out',(Join-Path $out 'release-import-checks.apk'),(Join-Path $out 'aligned.apk'))
} finally {
    Remove-Item Env:VERN_CHECK_STORE_PASS,Env:VERN_CHECK_KEY_PASS -ErrorAction SilentlyContinue
}
Invoke-Checked (Join-Path $buildTools 'apksigner.bat') @('verify','--print-certs',(Join-Path $out 'release-import-checks.apk'))
