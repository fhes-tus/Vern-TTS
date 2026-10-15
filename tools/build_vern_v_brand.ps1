param([Parameter(Mandatory=$true)][string]$Reference)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing,System.Core -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Globalization;
using System.Linq;
public static class VernVectorTrace {
    static string F(double value) { return value.ToString("0.###", CultureInfo.InvariantCulture); }
    static PointF Tangent(Point prev, Point cur, Point next) {
        double ax=cur.X-prev.X,ay=cur.Y-prev.Y,bx=next.X-cur.X,by=next.Y-cur.Y;
        double length=Math.Sqrt((ax*ax+ay*ay)*(bx*bx+by*by));
        if(length==0 || (ax*bx+ay*by)/length < .4) return new PointF(0,0);
        double tx=(next.X-prev.X)/6.0,ty=(next.Y-prev.Y)/6.0;
        double maxLength=Math.Min(Math.Sqrt(ax*ax+ay*ay),Math.Sqrt(bx*bx+by*by))/3;
        double tangentLength=Math.Sqrt(tx*tx+ty*ty), factor=tangentLength>maxLength ? maxLength/tangentLength : 1;
        return new PointF((float)(tx*factor),(float)(ty*factor));
    }
    static string Curve(List<Point> points, int minX, int minY) {
        if(points[0]==points[points.Count-1])points.RemoveAt(points.Count-1);
        int left=points.Min(p=>p.X)-minX,right=points.Max(p=>p.X)-minX,top=points.Min(p=>p.Y)-minY,bottom=points.Max(p=>p.Y)-minY;
        if(right-left<150){
            double r=(right-left)/2.0,cx=(right+left)/2.0,k=r*.55228475;
            return "M"+F(cx)+","+F(top)+" C"+F(cx+k)+","+F(top)+" "+F(right)+","+F(top+r-k)+" "+F(right)+","+F(top+r)+
                " L"+F(right)+","+F(bottom-r)+" C"+F(right)+","+F(bottom-r+k)+" "+F(cx+k)+","+F(bottom)+" "+F(cx)+","+F(bottom)+
                " C"+F(cx-k)+","+F(bottom)+" "+F(left)+","+F(bottom-r+k)+" "+F(left)+","+F(bottom-r)+
                " L"+F(left)+","+F(top+r)+" C"+F(left)+","+F(top+r-k)+" "+F(cx-k)+","+F(top)+" "+F(cx)+","+F(top)+" Z";
        }
        int n=points.Count;var text=new System.Text.StringBuilder();
        text.Append("M"+F(points[0].X-minX)+","+F(points[0].Y-minY));
        for(int i=0;i<n;i++){
            var a=points[i];var b=points[(i+1)%n];
            var ta=Tangent(points[(i+n-1)%n],a,b);var tb=Tangent(a,b,points[(i+2)%n]);
            text.Append(" C"+F(a.X+ta.X-minX)+","+F(a.Y+ta.Y-minY)+" "+F(b.X-tb.X-minX)+","+F(b.Y-tb.Y-minY)+" "+F(b.X-minX)+","+F(b.Y-minY));
        }
        return text.ToString()+" Z";
    }
    static double Distance(Point p, Point a, Point b) {
        double dx=b.X-a.X, dy=b.Y-a.Y;
        if(dx==0 && dy==0) return Math.Sqrt(Math.Pow(p.X-a.X,2)+Math.Pow(p.Y-a.Y,2));
        return Math.Abs(dy*p.X-dx*p.Y+b.X*a.Y-b.Y*a.X)/Math.Sqrt(dx*dx+dy*dy);
    }
    static List<Point> Simplify(List<Point> p) {
        if(p.Count<3) return p;
        double max=0; int at=0;
        for(int i=1;i<p.Count-1;i++){double d=Distance(p[i],p[0],p[p.Count-1]);if(d>max){max=d;at=i;}}
        if(max<=1.5) return new List<Point>{p[0],p[p.Count-1]};
        var left=Simplify(p.GetRange(0,at+1));var right=Simplify(p.GetRange(at,p.Count-at));
        left.RemoveAt(left.Count-1);left.AddRange(right);return left;
    }
    public static string Trace(string file) {
        using(var bmp=new Bitmap(file)) {
            int w=bmp.Width,h=bmp.Height;var mask=new bool[w,h];
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){var c=bmp.GetPixel(x,y);mask[x,y]=c.B-c.R>65 && c.R<180;}
            var edges=new Dictionary<Point,Point>();
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(mask[x,y]) {
                if(y==0||!mask[x,y-1])edges[new Point(x,y)]=new Point(x+1,y);
                if(x==w-1||!mask[x+1,y])edges[new Point(x+1,y)]=new Point(x+1,y+1);
                if(y==h-1||!mask[x,y+1])edges[new Point(x+1,y+1)]=new Point(x,y+1);
                if(x==0||!mask[x-1,y])edges[new Point(x,y+1)]=new Point(x,y);
            }
            var paths=new List<List<Point>>();
            while(edges.Count>0){var start=edges.Keys.First();var cur=start;var path=new List<Point>{start};
                do{Point next;if(!edges.TryGetValue(cur,out next))break;edges.Remove(cur);cur=next;path.Add(cur);}while(cur!=start);
                if(path.Count>100)paths.Add(Simplify(path));
            }
            if(paths.Count!=4)throw new Exception("Expected one silhouette and three audio holes, got "+paths.Count);
            var all=paths.SelectMany(p=>p).ToList();int minX=all.Min(p=>p.X),minY=all.Min(p=>p.Y);
            int width=all.Max(p=>p.X)-minX,height=all.Max(p=>p.Y)-minY;
            string data=String.Join(" ",paths.Select(p=>Curve(p,minX,minY)));
            return width+"|"+height+"|"+data;
        }
    }
}
'@
$parts = [VernVectorTrace]::Trace($Reference).Split('|')
$markWidth = [int]$parts[0]; $markHeight = [int]$parts[1]; $pathData = $parts[2]
$resRoot = Join-Path $PSScriptRoot '../app/src/main/res'
function WriteVector([string]$Path, [string]$Color, [bool]$Adaptive) {
    $group = ''
    $closeGroup = ''
    if ($Adaptive) {
        # The complete silhouette must fit the 66 dp circle, including its upper arms.
        $scale = (52.0 / $markWidth).ToString('0.######', [Globalization.CultureInfo]::InvariantCulture)
        $top = ((108 - $markHeight * 52.0 / $markWidth) / 2).ToString('0.######', [Globalization.CultureInfo]::InvariantCulture)
        $group = "<group android:scaleX=`"$scale`" android:scaleY=`"$scale`" android:translateX=`"28`" android:translateY=`"$top`">"
        $closeGroup = '</group>'
        $viewportWidth = 108; $viewportHeight = 108
    } else { $viewportWidth = $markWidth; $viewportHeight = $markHeight }
    $xml = @"
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="${viewportWidth}dp" android:height="${viewportHeight}dp" android:viewportWidth="$viewportWidth" android:viewportHeight="$viewportHeight">
    $group
    <path android:fillColor="$Color" android:fillType="evenOdd" android:pathData="$pathData" />
    $closeGroup
</vector>
"@
    [IO.File]::WriteAllText($Path, $xml)
}
WriteVector (Join-Path $resRoot 'drawable/vern_v_mark.xml') '#3C3C40' $false
New-Item -ItemType Directory -Force (Join-Path $resRoot 'drawable-night') | Out-Null
WriteVector (Join-Path $resRoot 'drawable-night/vern_v_mark.xml') '#FFF6E6' $false
WriteVector (Join-Path $resRoot 'drawable/vern_v_cream.xml') '#FFF6E6' $false
WriteVector (Join-Path $resRoot 'drawable/vern_icon_foreground.xml') '#FFF6E6' $true
WriteVector (Join-Path $resRoot 'drawable/vern_icon_monochrome.xml') '#FFFFFF' $true
WriteVector (Join-Path $resRoot 'drawable/ic_stat_veritas.xml') '#FFFFFF' $false
$notificationVector = [IO.File]::ReadAllText((Join-Path $resRoot 'drawable/ic_stat_veritas.xml'))
$notificationVector = $notificationVector.Replace('android:width="966dp"', 'android:width="24dp"').Replace('android:height="743dp"', 'android:height="24dp"')
$notificationVector = $notificationVector.Replace('android:viewportHeight="743"', 'android:viewportHeight="966"').Replace('<path ', '<group android:translateY="111.5"><path ').Replace('</vector>', '</group></vector>')
# Omit the small sound slots for a solid, readable status-bar silhouette.
$notificationVector = $notificationVector -replace ' Z M[^"\r\n]*', ' Z'
[IO.File]::WriteAllText((Join-Path $resRoot 'drawable/ic_stat_veritas.xml'), $notificationVector)
$splashVector = [IO.File]::ReadAllText((Join-Path $resRoot 'drawable/vern_icon_foreground.xml'))
# Animated splash assets use a 432 dp canvas (4 x 108), with a 288 dp visible area.
$splashVector = $splashVector.Replace('android:width="108dp"', 'android:width="432dp"').Replace('android:height="108dp"', 'android:height="432dp"')
$splashVector = $splashVector.Replace('<group android:scaleX=', '<group android:name="vernReveal" android:pivotX="54" android:pivotY="54"><group android:scaleX=')
$splashVector = $splashVector.Replace('<path android:fillColor=', '<path android:name="vernInk" android:fillColor=')
$splashVector = $splashVector.Replace('</group>', '</group></group>')
[IO.File]::WriteAllText((Join-Path $resRoot 'drawable/vern_splash_mark.xml'), $splashVector)
# Render the same vector paths for legacy launchers, notification artwork and store assets.
$vectorPaths = [System.Drawing.Drawing2D.GraphicsPath]::new([System.Drawing.Drawing2D.FillMode]::Alternate)
foreach ($contour in ($pathData -split ' Z' | Where-Object { $_.Trim() })) {
    $coordinatePattern = '(-?[\d.]+),(-?[\d.]+)'
    $startPoint = [regex]::Match($contour, 'M' + $coordinatePattern)
    $previous = [System.Drawing.PointF]::new([float]$startPoint.Groups[1].Value, [float]$startPoint.Groups[2].Value)
    $vectorPaths.StartFigure()
    foreach ($segment in ([regex]::Matches($contour, 'L' + $coordinatePattern + '|C' + $coordinatePattern + ' ' + $coordinatePattern + ' ' + $coordinatePattern))) {
        $coordinates = @([regex]::Matches($segment.Value, $coordinatePattern) | ForEach-Object {
            [System.Drawing.PointF]::new([float]$_.Groups[1].Value, [float]$_.Groups[2].Value)
        })
        if ($segment.Value.StartsWith('L')) {
            $vectorPaths.AddLine($previous, $coordinates[0]); $previous = $coordinates[0]
        } else {
            $vectorPaths.AddBezier($previous, $coordinates[0], $coordinates[1], $coordinates[2]); $previous = $coordinates[2]
        }
    }
    $vectorPaths.CloseFigure()
}
function RenderBrand([string]$File, [int]$Side, [System.Drawing.Color]$Background, [System.Drawing.Color]$Ink) {
    $bitmap = [System.Drawing.Bitmap]::new($Side, $Side)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.Clear($Background)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.TranslateTransform($Side * .115, ($Side - $Side * .77 * $markHeight / $markWidth) / 2)
    $graphics.ScaleTransform($Side * .77 / $markWidth, $Side * .77 / $markWidth)
    $brush = [System.Drawing.SolidBrush]::new($Ink)
    $graphics.FillPath($brush, $vectorPaths)
    $bitmap.Save($File, [System.Drawing.Imaging.ImageFormat]::Png)
    $brush.Dispose(); $graphics.Dispose(); $bitmap.Dispose()
}
$indigo = [System.Drawing.ColorTranslator]::FromHtml('#4540D8')
$cream = [System.Drawing.ColorTranslator]::FromHtml('#FFF6E6')
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
RenderBrand (Join-Path $resRoot 'drawable-nodpi/veritas_reader_icon.png') 512 $indigo $cream
RenderBrand (Join-Path $repoRoot 'docs/icon.png') 512 $indigo $cream
RenderBrand (Join-Path $repoRoot 'docs/playstore_icon_512.png') 512 $indigo $cream
RenderBrand (Join-Path $repoRoot 'docs/brand/vern-launcher-preview.png') 1024 $indigo $cream
RenderBrand (Join-Path $repoRoot 'docs/brand/icon-mode-previews/image-1-black-transparent.png') 1024 ([System.Drawing.Color]::Transparent) ([System.Drawing.Color]::Black)
RenderBrand (Join-Path $repoRoot 'docs/brand/icon-mode-previews/image-1-cream-transparent.png') 1024 ([System.Drawing.Color]::Transparent) $cream
RenderBrand (Join-Path $repoRoot 'docs/brand/icon-mode-previews/image-1-charcoal-transparent.png') 1024 ([System.Drawing.Color]::Transparent) ([System.Drawing.ColorTranslator]::FromHtml('#3C3C40'))
$vectorPaths.Dispose()
& (Join-Path $PSScriptRoot 'generate_vern_icon_palettes.ps1')
Write-Output "Traced big-head V: ${markWidth}x${markHeight}; silhouette plus three transparent slots."
