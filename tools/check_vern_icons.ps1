$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$res = Join-Path $repo 'app/src/main/res'
[xml]$xml = Get-Content (Join-Path $res 'drawable/vern_icon_foreground.xml')
$ns = [Xml.XmlNamespaceManager]::new($xml.NameTable)
$ns.AddNamespace('android', 'http://schemas.android.com/apk/res/android')
$group = $xml.SelectSingleNode('/vector/group')
$scale = [double]::Parse($group.GetAttribute('scaleX', $ns.LookupNamespace('android')), [Globalization.CultureInfo]::InvariantCulture)
$tx = [double]::Parse($group.GetAttribute('translateX', $ns.LookupNamespace('android')), [Globalization.CultureInfo]::InvariantCulture)
$ty = [double]::Parse($group.GetAttribute('translateY', $ns.LookupNamespace('android')), [Globalization.CultureInfo]::InvariantCulture)
$data = $xml.SelectSingleNode('/vector/group/path').GetAttribute('pathData', $ns.LookupNamespace('android'))
$path = [Drawing.Drawing2D.GraphicsPath]::new([Drawing.Drawing2D.FillMode]::Alternate)
$pair = '(-?[\d.]+),(-?[\d.]+)'
foreach ($contour in ($data -split ' Z' | Where-Object { $_.Trim() })) {
    $start = [regex]::Match($contour, 'M' + $pair)
    $previous = [Drawing.PointF]::new([float]$start.Groups[1].Value, [float]$start.Groups[2].Value)
    $path.StartFigure()
    foreach ($segment in [regex]::Matches($contour, 'L' + $pair + '|C' + $pair + ' ' + $pair + ' ' + $pair)) {
        $points = @([regex]::Matches($segment.Value, $pair) | ForEach-Object { [Drawing.PointF]::new([float]$_.Groups[1].Value, [float]$_.Groups[2].Value) })
        if ($segment.Value.StartsWith('L')) { $path.AddLine($previous, $points[0]); $previous = $points[0] }
        else { $path.AddBezier($previous, $points[0], $points[1], $points[2]); $previous = $points[2] }
    }
    $path.CloseFigure()
}
$transform = [Drawing.Drawing2D.Matrix]::new([float]$scale, 0, 0, [float]$scale, [float]$tx, [float]$ty)
$path.Transform($transform)
$flat = $path.Clone()
$flat.Flatten([Drawing.Drawing2D.Matrix]::new(), 0.001)
$radius = ($flat.PathPoints | ForEach-Object { [Math]::Sqrt([Math]::Pow($_.X - 54, 2) + [Math]::Pow($_.Y - 54, 2)) } | Measure-Object -Maximum).Maximum
if ($radius -gt 32.99) { throw "Logo exceeds 66 dp safe circle: radius $radius" }
[xml]$mono = Get-Content (Join-Path $res 'drawable/vern_icon_monochrome.xml')
if ($mono.vector.group.path.pathData -ne $data) { throw 'Monochrome silhouette differs from foreground' }
[xml]$splash = Get-Content (Join-Path $res 'drawable/vern_splash_mark.xml')
if ($splash.vector.width -ne '432dp' -or $splash.vector.height -ne '432dp') { throw 'Animated splash canvas must be 432 dp' }
foreach ($name in @('ic_launcher', 'ic_launcher_round')) {
    [xml]$adaptive = Get-Content (Join-Path $res "mipmap-anydpi-v26/$name.xml")
    foreach ($layer in @('background', 'foreground', 'monochrome')) {
        if (-not $adaptive.'adaptive-icon'.$layer) { throw "Missing $layer in $name" }
    }
}
# Preview the visible 72 dp area under three launcher masks.
$bitmap = [Drawing.Bitmap]::new(960, 350)
$g = [Drawing.Graphics]::FromImage($bitmap)
$g.Clear([Drawing.ColorTranslator]::FromHtml('#ECECF2'))
$g.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
$ink = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml('#FFF6E6'))
$background = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml('#4540D8'))
$font = [Drawing.Font]::new('Segoe UI', 14)
$names = @('Circle', 'Squircle', 'Rounded square')
for ($i = 0; $i -lt 3; $i++) {
    $left = 30 + $i * 320
    $mask = [Drawing.Drawing2D.GraphicsPath]::new()
    if ($i -eq 0) { $mask.AddEllipse($left, 20, 260, 260) }
    elseif ($i -eq 1) {
        $points = for ($j = 0; $j -lt 360; $j++) {
            $a = $j * [Math]::PI / 180; $c = [Math]::Cos($a); $s = [Math]::Sin($a)
            [Drawing.PointF]::new(($left + 130 + 130 * [Math]::Sign($c) * [Math]::Pow([Math]::Abs($c), 0.5)), (150 + 130 * [Math]::Sign($s) * [Math]::Pow([Math]::Abs($s), 0.5)))
        }
        $mask.AddPolygon([Drawing.PointF[]]$points)
    } else {
        $mask.AddArc($left, 20, 76, 76, 180, 90)
        $mask.AddArc($left + 184, 20, 76, 76, 270, 90)
        $mask.AddArc($left + 184, 204, 76, 76, 0, 90)
        $mask.AddArc($left, 204, 76, 76, 90, 90)
        $mask.CloseFigure()
    }
    $state = $g.Save()
    $g.SetClip($mask)
    $g.FillPath($background, $mask)
    $g.TranslateTransform(($left - 18 * 260 / 72), (20 - 18 * 260 / 72))
    $g.ScaleTransform((260 / 72), (260 / 72))
    $g.FillPath($ink, $path)
    $g.Restore($state)
    $g.DrawString($names[$i], $font, [Drawing.Brushes]::Black, $left + 25, 300)
    $mask.Dispose()
}
$output = Join-Path $repo 'docs/brand/vern-adaptive-mask-check.png'
$bitmap.Save($output, [Drawing.Imaging.ImageFormat]::Png)
$font.Dispose(); $ink.Dispose(); $background.Dispose(); $g.Dispose(); $bitmap.Dispose(); $flat.Dispose(); $path.Dispose(); $transform.Dispose()
Write-Output "Safe circle passed: radius $([Math]::Round($radius, 3)) dp < 33 dp. Monochrome, splash dimensions, and adaptive layers passed."
Write-Output "Preview: $output"
