# Genera los PNG de marca (banner de TV, logo de bienvenida e íconos para Android 7).
# El ícono adaptativo (Android 8+) está en vectores en res/drawable/ic_launcher_*.xml
# y usa las mismas coordenadas (lienzo de 108 unidades) que este script.
#   powershell -ExecutionPolicy Bypass -File tools\branding.ps1
Add-Type -AssemblyName System.Drawing

$res = Join-Path $PSScriptRoot '..\app\src\main\res'
$ink = [System.Drawing.Color]::FromArgb(255, 7, 8, 12)
$navy = [System.Drawing.Color]::FromArgb(255, 16, 40, 79)
$accent = [System.Drawing.Color]::FromArgb(255, 138, 180, 248)
$screenFill = [System.Drawing.Color]::FromArgb(255, 11, 27, 51)
$white = [System.Drawing.Color]::White

$fontName = 'Segoe UI Black'
if (-not ((New-Object System.Drawing.Text.InstalledFontCollection).Families.Name -contains $fontName)) { $fontName = 'Arial Black' }

function New-Canvas([int]$w, [int]$h) {
    $bmp = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.InterpolationMode = 'HighQualityBicubic'
    $g.PixelOffsetMode = 'HighQuality'
    $g.TextRenderingHint = 'AntiAliasGridFit'
    return @($bmp, $g)
}

function RoundRect([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = 2 * $r
    $p.AddArc($x, $y, $d, $d, 180, 90)
    $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $p.CloseFigure()
    return $p
}

# Televisor con "play", en unidades del lienzo de 108: pantalla 30..78 x 34..70.
function Draw-TvMark($g, [float]$ox, [float]$oy, [float]$s) {
    $screen = RoundRect ($ox + 30 * $s) ($oy + 34 * $s) (48 * $s) (36 * $s) (6 * $s)
    $g.FillPath((New-Object System.Drawing.SolidBrush $screenFill), $screen)
    $pen = New-Object System.Drawing.Pen $white, (4 * $s)
    $pen.LineJoin = 'Round'
    $g.DrawPath($pen, $screen)
    $stand = RoundRect ($ox + 44 * $s) ($oy + 75 * $s) (20 * $s) (4 * $s) (2 * $s)
    $g.FillPath((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(217, 255, 255, 255))), $stand)
    $tri = [System.Drawing.PointF[]]@(
        (New-Object System.Drawing.PointF ($ox + 48 * $s), ($oy + 43 * $s)),
        (New-Object System.Drawing.PointF ($ox + 63 * $s), ($oy + 52 * $s)),
        (New-Object System.Drawing.PointF ($ox + 48 * $s), ($oy + 61 * $s))
    )
    $g.FillPolygon((New-Object System.Drawing.SolidBrush $accent), $tri)
}

function Draw-Wordmark($g, [float]$x, [float]$y, [float]$size) {
    $font = New-Object System.Drawing.Font $fontName, $size, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
    $fmt = [System.Drawing.StringFormat]::GenericTypographic
    $entreW = $g.MeasureString('Entre', $font, 10000, $fmt).Width
    $g.DrawString('Entre', $font, (New-Object System.Drawing.SolidBrush $white), $x, $y, $fmt)
    $g.DrawString('TV', $font, (New-Object System.Drawing.SolidBrush $accent), ($x + $entreW), $y, $fmt)
}

function Measure-Wordmark($g, [float]$size) {
    $font = New-Object System.Drawing.Font $fontName, $size, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
    $fmt = [System.Drawing.StringFormat]::GenericTypographic
    return $g.MeasureString('EntreTV', $font, 10000, $fmt)
}

function Fill-Gradient($g, [int]$w, [int]$h) {
    $rect = New-Object System.Drawing.Rectangle 0, 0, $w, $h
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect, $navy, $ink, 45
    $g.FillRectangle($brush, $rect)
}

function Save($bmp, $g, [string]$rel) {
    $path = Join-Path $res $rel
    New-Item -ItemType Directory -Force -Path (Split-Path $path) | Out-Null
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
}

# ---- Banner de Android TV: 320x180 dp (xhdpi = 640x360) ----
$c = New-Canvas 640 360; $bmp = $c[0]; $g = $c[1]
Fill-Gradient $g 640 360
$s = 3.4
$markW = 48 * $s; $markH = 45 * $s
$fontSize = 104
while ((Measure-Wordmark $g $fontSize).Width + $markW + 28 -gt 560) { $fontSize -= 2 }
$tm = Measure-Wordmark $g $fontSize
$total = $markW + 28 + $tm.Width
$left = (640 - $total) / 2
Draw-TvMark $g ($left - 30 * $s) ((360 - $markH) / 2 - 34 * $s) $s
Draw-Wordmark $g ($left + $markW + 28) ((360 - $tm.Height) / 2) $fontSize
Save $bmp $g 'drawable-xhdpi\tv_banner.png'

# ---- Logo de bienvenida: ícono sin fondo de 288 dp (xxhdpi = 864 px), visible en un círculo de 192 dp ----
$c = New-Canvas 864 864; $bmp = $c[0]; $g = $c[1]
$g.Clear([System.Drawing.Color]::Transparent)
$s = 5.0
$markW = 48 * $s; $markH = 45 * $s
$fontSize = 92
$tm = Measure-Wordmark $g $fontSize
$gap = 26
$blockH = $markH + $gap + $tm.Height
$top = (864 - $blockH) / 2
Draw-TvMark $g ((864 - $markW) / 2 - 30 * $s) ($top - 34 * $s) $s
Draw-Wordmark $g ((864 - $tm.Width) / 2) ($top + $markH + $gap) $fontSize
Save $bmp $g 'drawable-xxhdpi\splash_logo.png'

# ---- Íconos para Android 7 (sin ícono adaptativo) ----
$sizes = @{ 'mdpi' = 48; 'hdpi' = 72; 'xhdpi' = 96; 'xxhdpi' = 144; 'xxxhdpi' = 192 }
foreach ($d in $sizes.Keys) {
    $px = $sizes[$d]
    $s = $px / 72.0
    foreach ($round in @($false, $true)) {
        $c = New-Canvas $px $px; $bmp = $c[0]; $g = $c[1]
        $g.Clear([System.Drawing.Color]::Transparent)
        $shape = if ($round) {
            $p = New-Object System.Drawing.Drawing2D.GraphicsPath; $p.AddEllipse(0, 0, $px - 1, $px - 1); $p
        } else { RoundRect 0 0 ($px - 1) ($px - 1) ($px * 0.22) }
        $rect = New-Object System.Drawing.Rectangle 0, 0, $px, $px
        $g.FillPath((New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect, $navy, $ink, 45), $shape)
        Draw-TvMark $g (-18 * $s) (-18 * $s) $s
        $name = if ($round) { 'ic_launcher_round.png' } else { 'ic_launcher.png' }
        Save $bmp $g "mipmap-$d\$name"
    }
}
Write-Output "Listo ($fontName)"
