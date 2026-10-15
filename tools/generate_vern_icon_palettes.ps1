$ErrorActionPreference = 'Stop'
$paletteRepo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$paletteRes = Join-Path $paletteRepo 'app/src/main/res'
$paletteSource = [IO.File]::ReadAllText((Join-Path $paletteRepo 'app/src/main/java/com/veritas/reader/VernCustomization.kt'))
$paletteMatches = [regex]::Matches($paletteSource, 'VernIconPalette\("([a-z]+)", "[^"]+", 0xFF([A-F0-9]{6})\.toInt\(\), 0xFF([A-F0-9]{6})\.toInt\(\)\)')
if ($paletteMatches.Count -lt 6) { throw 'Missing Vern icon palettes' }
$paletteForeground = [IO.File]::ReadAllText((Join-Path $paletteRes 'drawable/vern_icon_foreground.xml'))
$paletteBackground = [IO.File]::ReadAllText((Join-Path $paletteRes 'drawable/vern_icon_background.xml'))
$paletteSplash = [IO.File]::ReadAllText((Join-Path $paletteRes 'drawable/vern_splash_mark.xml'))
$paletteAnimated = [IO.File]::ReadAllText((Join-Path $paletteRes 'drawable/vern_splash_animated.xml'))
$paletteAdaptive = [IO.File]::ReadAllText((Join-Path $paletteRes 'mipmap-anydpi-v26/ic_launcher.xml'))
foreach ($paletteMatch in $paletteMatches) {
    $paletteId = $paletteMatch.Groups[1].Value
    $paletteBg = '#' + $paletteMatch.Groups[2].Value
    $paletteFg = '#' + $paletteMatch.Groups[3].Value
    $paletteSuffix = if ($paletteId -eq 'indigo') { '' } else { '_' + $paletteId }
    if ($paletteSuffix) {
        [IO.File]::WriteAllText((Join-Path $paletteRes "drawable/vern_icon_foreground$paletteSuffix.xml"), $paletteForeground.Replace('#FFF6E6', $paletteFg))
        [IO.File]::WriteAllText((Join-Path $paletteRes "drawable/vern_icon_background$paletteSuffix.xml"), $paletteBackground.Replace('#4540D8', $paletteBg))
        [IO.File]::WriteAllText((Join-Path $paletteRes "drawable/vern_splash_mark$paletteSuffix.xml"), $paletteSplash.Replace('#FFF6E6', $paletteFg))
        [IO.File]::WriteAllText((Join-Path $paletteRes "drawable/vern_splash_animated$paletteSuffix.xml"), $paletteAnimated.Replace('@drawable/vern_splash_mark', "@drawable/vern_splash_mark$paletteSuffix"))
        [IO.File]::WriteAllText((Join-Path $paletteRes "mipmap-anydpi-v26/vern_launcher$paletteSuffix.xml"),
            $paletteAdaptive.Replace('@drawable/vern_icon_background', "@drawable/vern_icon_background$paletteSuffix").Replace('@drawable/vern_icon_foreground', "@drawable/vern_icon_foreground$paletteSuffix"))
    }
    [IO.File]::WriteAllText((Join-Path $paletteRes "drawable/vern_starting_mark_$paletteId.xml"), $paletteForeground.Replace('#FFF6E6', $paletteFg).Replace('108dp', '192dp'))
}
Write-Output "Generated $($paletteMatches.Count) palettes from the current V geometry and Kotlin palette colours."
