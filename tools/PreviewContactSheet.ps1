param([string]$PreviewDirectory)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = (Resolve-Path -LiteralPath $PreviewDirectory).Path
$canvas = [System.Drawing.Bitmap]::new(1248, 2056)
$graphics = [System.Drawing.Graphics]::FromImage($canvas)
$font = [System.Drawing.Font]::new('Segoe UI', 18)
try {
    $graphics.Clear([System.Drawing.Color]::FromArgb(22, 27, 34))
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    for ($index = 1; $index -le 8; $index++) {
        $column = ($index - 1) % 2
        $row = [Math]::Floor(($index - 1) / 2)
        $x = 16 + $column * 616
        $y = 16 + $row * 510
        $file = Join-Path $root ('{0:D2}.png' -f $index)
        $preview = [System.Drawing.Image]::FromFile($file)
        try {
            $graphics.DrawString(('{0:D2}' -f $index), $font, [System.Drawing.Brushes]::White, $x, $y)
            $graphics.DrawImage($preview, [int]$x, [int]($y + 38), 600, 450)
        } finally { $preview.Dispose() }
    }
    $output = Join-Path $root 'contact-sheet.png'
    $canvas.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output $output
} finally {
    $font.Dispose()
    $graphics.Dispose()
    $canvas.Dispose()
}
