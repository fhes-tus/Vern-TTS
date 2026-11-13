param(
    [Parameter(Mandatory=$true)][string]$ExtendedSources,
    [Parameter(Mandatory=$true)][string]$CoreSources,
    [switch]$CheckOnly
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repoRoot = [IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent))
$vendorRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'app/src/main/java/androidx/compose/material/icons'))
$stylePattern = '(?:automirrored\.)?(?:filled|outlined|rounded|sharp|twotone)'
$extended = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $ExtendedSources).Path)
$core = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $CoreSources).Path)

function Get-IconIndex($archive) {
    $index = [Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '^commonMain/androidx/compose/material/icons/(?<style>(?:automirrored/)?(?:filled|outlined|rounded|sharp|twotone))/(?<name>\w+)\.kt$') {
            $index.Add(($Matches.style.Replace('/','.') + '.' + $Matches.name), $entry)
        }
    }
    return ,$index
}

try {
    $extendedIndex = Get-IconIndex $extended
    $coreIndex = Get-IconIndex $core
    $selected = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $pending = [Collections.Generic.Queue[string]]::new()
    $sourceBytes = [Collections.Generic.Dictionary[string,byte[]]]::new([StringComparer]::Ordinal)
    $wildcardFiles = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)

    function Add-Icon([string]$key, [string]$origin) {
        if ($extendedIndex.ContainsKey($key)) {
            if ($coreIndex.ContainsKey($key)) { throw "Icon exists in both libraries: $key" }
            if ($selected.Add($key)) { $pending.Enqueue($key) }
        } elseif (-not $coreIndex.ContainsKey($key)) {
            throw "Unknown icon reference '$key' in $origin; refusing an incomplete extraction."
        }
    }

    function Add-References([string]$text, [string]$origin) {
        $imports = [regex]::Matches($text, '(?m)^[ \t]*import[ \t]+androidx\.compose\.material\.icons\.(?<path>[\w.*]+)(?:[ \t]+as[ \t]+\w+)?[ \t]*;?[ \t]*\r?$')
        foreach ($import in $imports) {
            $name = $import.Groups['path'].Value
            if ($name -match "^(?<style>$stylePattern)\.(?<icon>\w+)$") {
                Add-Icon ($Matches.style + '.' + $Matches.icon) $origin
            } elseif ($name -match "^(?<style>$stylePattern)\.\*$") {
                # Conservatively keep any named export mentioned in a wildcard-importing file.
                # This covers aliased receivers as well as direct Icons.Style.Name calls.
                $prefix = $Matches.style + '.'
                $null = $wildcardFiles.Add($origin)
                $words = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
                foreach ($word in [regex]::Matches($text, '\b[A-Za-z_]\w*\b')) { $null = $words.Add($word.Value) }
                foreach ($key in $extendedIndex.Keys) {
                    if ($key.StartsWith($prefix, [StringComparison]::Ordinal) -and $words.Contains($key.Substring($prefix.Length))) {
                        Add-Icon $key $origin
                    }
                }
            }
        }
        foreach ($reference in [regex]::Matches($text, '\bIcons\.(?<style>AutoMirrored\.(?:Filled|Outlined|Rounded|Sharp|TwoTone)|Default|Filled|Outlined|Rounded|Sharp|TwoTone)\.(?<icon>\w+)')) {
            $style = $reference.Groups['style'].Value.ToLowerInvariant().Replace('default','filled')
            Add-Icon ($style + '.' + $reference.Groups['icon'].Value) $origin
        }
    }

    $files = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot 'app/src'),(Join-Path $repoRoot 'menu-translation/src') -Recurse -File |
        Where-Object { $_.Extension -in '.kt','.java' -and -not $_.FullName.StartsWith($vendorRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) })
    foreach ($file in $files) { Add-References ([IO.File]::ReadAllText($file.FullName)) $file.FullName.Substring($repoRoot.Length + 1) }
    # Legacy/deprecated definitions can refer to auto-mirrored definitions: include that closure.
    while ($pending.Count -gt 0) {
        $key = $pending.Dequeue()
        $inputStream = $extendedIndex[$key].Open()
        $buffer = [IO.MemoryStream]::new()
        try { $inputStream.CopyTo($buffer); $bytes = $buffer.ToArray() } finally { $inputStream.Dispose(); $buffer.Dispose() }
        $sourceBytes.Add($key, $bytes)
        Add-References ([Text.Encoding]::UTF8.GetString($bytes)) $extendedIndex[$key].FullName
    }
    if ($selected.Count -eq 0) { throw 'No extended icons found; refusing an empty extraction.' }

    $manifest = [Collections.Generic.List[object]]::new()
    # Validate every destination before writing. Existing differing files are never overwritten.
    foreach ($key in ($selected | Sort-Object)) {
        $entry = $extendedIndex[$key]
        $relative = 'app/src/main/java/' + $entry.FullName.Substring('commonMain/'.Length)
        $target = [IO.Path]::GetFullPath((Join-Path $repoRoot $relative))
        if (-not $target.StartsWith($vendorRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe icon destination.' }
        $bytes = $sourceBytes[$key]
        if (Test-Path -LiteralPath $target) {
            $actual = [IO.File]::ReadAllBytes($target)
            if ([Convert]::ToBase64String($actual) -cne [Convert]::ToBase64String($bytes)) { throw "Existing local icon differs from upstream: $relative" }
        } elseif ($CheckOnly) { throw "Missing local icon: $relative" }
        $manifest.Add([pscustomobject]@{icon=$key;source=$entry.FullName;path=$relative;bytes=$bytes.Length;sha256=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()})
    }
    if (-not $CheckOnly) {
        foreach ($item in $manifest) {
            $target = Join-Path $repoRoot $item.path
            $null = [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target))
            if (-not (Test-Path -LiteralPath $target)) { [IO.File]::WriteAllBytes($target, $sourceBytes[$item.icon]) }
        }
        $noticeRoot = Join-Path $repoRoot 'third_party/material-icons'
        $null = [IO.Directory]::CreateDirectory($noticeRoot)
        $record = [ordered]@{
            artifact='androidx.compose.material:material-icons-extended-android:1.7.8'
            sourceJarSha256=(Get-FileHash -LiteralPath $ExtendedSources -Algorithm SHA256).Hash.ToLowerInvariant()
            coreArtifact='androidx.compose.material:material-icons-core-android:1.7.8'
            selection='Explicit imports, direct references, conservative wildcard matches, and upstream definition dependencies across Android source sets.'
            icons=$manifest
        }
        [IO.File]::WriteAllText((Join-Path $noticeRoot 'manifest.json'), ($record | ConvertTo-Json -Depth 5) + "`n", [Text.UTF8Encoding]::new($false))
        $copyrights = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($bytes in $sourceBytes.Values) {
            foreach ($match in [regex]::Matches([Text.Encoding]::UTF8.GetString($bytes), '(?m)^ \* (Copyright .+)\r?$')) { $null = $copyrights.Add($match.Groups[1].Value.Trim()) }
        }
        $notice = "Material Icons Extended 1.7.8`n`n" + (($copyrights | Sort-Object) -join "`n") + "`n`nSelected Kotlin icon definitions are copied unchanged from AndroidX.`nLicensed under the Apache License, Version 2.0.`nOriginal notices remain in each source file.`nhttps://developer.android.com/jetpack/androidx/releases/compose-material`n"
        $licenseRoot = Join-Path $repoRoot 'app/src/main/assets/licenses'
        [IO.File]::WriteAllText((Join-Path $licenseRoot 'MaterialIconsExtended-NOTICE.txt'), $notice, [Text.UTF8Encoding]::new($false))
        [IO.File]::Copy((Join-Path $repoRoot 'LICENSE'), (Join-Path $licenseRoot 'MaterialIconsExtended-LICENSE.txt'), $true)
    }
    [pscustomobject]@{mode= $(if ($CheckOnly) {'Check'} else {'Extract'});scannedFiles=$files.Count;selectedExtendedIcons=$selected.Count;sourceBytes=($manifest|Measure-Object bytes -Sum).Sum;wildcardFiles=$wildcardFiles.Count;allSelectedFilesMatchUpstream=$true} | ConvertTo-Json -Compress
} finally {
    $extended.Dispose()
    $core.Dispose()
}
