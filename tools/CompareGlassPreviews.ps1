param([string]$BeforeDirectory, [string]$AfterDirectory)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$before = (Resolve-Path -LiteralPath $BeforeDirectory).Path
$after = (Resolve-Path -LiteralPath $AfterDirectory).Path
$canvas = [System.Drawing.Bitmap]::new(1248, 1040)
$graphics = [System.Drawing.Graphics]::FromImage($canvas)
$font = [System.Drawing.Font]::new('Segoe UI', 18)
try {
    $graphics.Clear([System.Drawing.Color]::FromArgb(22, 27, 34))
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $samples = @(1, 8)
    for ($row = 0; $row -lt $samples.Count; $row++) {
        for ($column = 0; $column -lt 2; $column++) {
            $root = if ($column -eq 0) { $before } else { $after }
            $caption = if ($column -eq 0) { 'No blur' } else { 'Glass / radius 24 px' }
            $x = 16 + $column * 616
            $y = 16 + $row * 510
            $file = Join-Path $root ('{0:D2}.png' -f $samples[$row])
            $preview = [System.Drawing.Image]::FromFile($file)
            try {
                $graphics.DrawString(('{0:D2}  /  {1}' -f $samples[$row], $caption), $font, [System.Drawing.Brushes]::White, $x, $y)
                $graphics.DrawImage($preview, [int]$x, [int]($y + 38), 600, 450)
            } finally { $preview.Dispose() }
        }
    }
    $output = Join-Path $after 'before-after.png'
    $canvas.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output $output
} finally {
    $font.Dispose()
    $graphics.Dispose()
    $canvas.Dispose()
}
