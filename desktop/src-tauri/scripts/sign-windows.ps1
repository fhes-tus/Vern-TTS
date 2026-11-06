[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string] $FilePath
)

$ErrorActionPreference = 'Stop'

$thumbprint = ($env:VERN_SIGNING_THUMBPRINT -replace '\s', '').ToUpperInvariant()
if ($thumbprint -notmatch '^[A-F0-9]{40}$') {
    throw 'Set VERN_SIGNING_THUMBPRINT to the 40-character thumbprint of the issued code-signing certificate in CurrentUser\My.'
}

$timestampUrl = $env:VERN_TIMESTAMP_URL
$parsedTimestampUrl = $null
if (-not [Uri]::TryCreate($timestampUrl, [UriKind]::Absolute, [ref] $parsedTimestampUrl) -or
    $parsedTimestampUrl.Scheme -notin @('https', 'http')) {
    throw 'Set VERN_TIMESTAMP_URL to the RFC 3161 timestamp endpoint supplied by the certificate provider.'
}

$certificate = Get-Item -LiteralPath "Cert:\CurrentUser\My\$thumbprint" -ErrorAction Stop
if (-not $certificate.HasPrivateKey) {
    throw 'The selected signing certificate has no available private key.'
}
$hasCodeSigningUsage = $false
foreach ($extension in $certificate.Extensions) {
    if ($extension -is [System.Security.Cryptography.X509Certificates.X509EnhancedKeyUsageExtension]) {
        foreach ($usage in $extension.EnhancedKeyUsages) {
            if ($usage.Value -eq '1.3.6.1.5.5.7.3.3') {
                $hasCodeSigningUsage = $true
            }
        }
    }
}
if (-not $hasCodeSigningUsage) {
    throw 'The selected certificate does not include the Code Signing usage.'
}

$resolvedFile = (Resolve-Path -LiteralPath $FilePath).Path
$signToolPath = (Get-Command 'signtool.exe' -ErrorAction SilentlyContinue).Source
if (-not $signToolPath) {
    $installedRoots = 'HKLM:\SOFTWARE\Microsoft\Windows Kits\Installed Roots'
    $kitsRoot = (Get-ItemProperty -LiteralPath $installedRoots -Name KitsRoot10 -ErrorAction SilentlyContinue).KitsRoot10
    if ($kitsRoot) {
        $sdkDirectories = Get-ChildItem -LiteralPath (Join-Path $kitsRoot 'bin') -Directory -ErrorAction SilentlyContinue |
            Sort-Object { try { [version] $_.Name } catch { [version] '0.0' } } -Descending
        foreach ($sdkDirectory in $sdkDirectories) {
            $candidate = Join-Path $sdkDirectory.FullName 'x64\signtool.exe'
            if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                $signToolPath = $candidate
                break
            }
        }
    }
}
if (-not $signToolPath) {
    throw 'Windows SDK SignTool was not found. Install the Windows SDK or add signtool.exe to PATH.'
}
$signArguments = @(
    'sign', '/v', '/fd', 'SHA256', '/sha1', $thumbprint, '/s', 'My',
    '/tr', $timestampUrl, '/td', 'SHA256', $resolvedFile
)
& $signToolPath @signArguments
if ($LASTEXITCODE -ne 0) {
    throw "SignTool failed to sign the build artifact (exit code $LASTEXITCODE)."
}

$verifyArguments = @('verify', '/pa', '/all', '/v', $resolvedFile)
& $signToolPath @verifyArguments
if ($LASTEXITCODE -ne 0) {
    throw "SignTool could not verify the signed build artifact (exit code $LASTEXITCODE)."
}

$signature = Get-AuthenticodeSignature -LiteralPath $resolvedFile
if ($signature.Status -ne 'Valid' -or
    $null -eq $signature.SignerCertificate -or
    $signature.SignerCertificate.Thumbprint.ToUpperInvariant() -ne $thumbprint) {
    throw 'The build artifact does not have a valid signature from the selected publisher certificate.'
}

Write-Output "Verified Authenticode signature: $resolvedFile"
