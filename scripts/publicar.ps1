# Publica una versión nueva de Camstyling:
#   1. sube el número (parche, menor o mayor) en gradle.properties, o usa -Version
#   2. convierte "Sin publicar" del CHANGELOG en la sección de esa versión
#   3. compila el APK firmado -> release/Camstyling-vX.Y.Z.apk
#   4. commit, etiqueta vX.Y.Z, push y Release de GitHub con el APK y las notas
# Después, Claude agrega el enlace de la versión al documento "Camstyling -
# Descargas" de Google Drive (ver README, "Comandos").
#
# Uso:  .\scripts\publicar.ps1 parche      (0.1.0 -> 0.1.1)
#       .\scripts\publicar.ps1 menor       (0.1.3 -> 0.2.0)
#       .\scripts\publicar.ps1 -Version 0.1.0

param(
    [ValidateSet('parche', 'menor', 'mayor')] [string] $Tipo = 'parche',
    [string] $Version
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path $PSScriptRoot -Parent
Set-Location $raiz

if (git status --porcelain) { throw 'Hay cambios sin commit. Haz commit antes de publicar.' }
if (-not (Test-Path keystore.properties)) { throw 'Falta keystore.properties (la llave de firma). Ver README, "Firma".' }

$utf8 = New-Object System.Text.UTF8Encoding $false
$propiedades = [IO.File]::ReadAllText("$raiz\gradle.properties", $utf8)
$actual = [regex]::Match($propiedades, 'camstyling\.version=(\d+\.\d+\.\d+)').Groups[1].Value

if (-not $Version) {
    $m, $n, $p = $actual.Split('.') | ForEach-Object { [int]$_ }
    switch ($Tipo) {
        'mayor'  { $m++; $n = 0; $p = 0 }
        'menor'  { $n++; $p = 0 }
        'parche' { $p++ }
    }
    $Version = "$m.$n.$p"
}
if (git tag --list "v$Version") { throw "La etiqueta v$Version ya existe." }

# 1. Número de versión
$propiedades = $propiedades -replace 'camstyling\.version=\d+\.\d+\.\d+', "camstyling.version=$Version"
[IO.File]::WriteAllText("$raiz\gradle.properties", $propiedades, $utf8)

# 2. CHANGELOG: "Sin publicar" pasa a ser "X.Y.Z (fecha)" y queda una vacía arriba
$changelog = [IO.File]::ReadAllText("$raiz\CHANGELOG.md", $utf8)
$fecha = Get-Date -Format 'yyyy-MM-dd'
$seccion = [regex]::Match($changelog, '(?s)## Sin publicar\r?\n(.*?)(?=\r?\n## |\z)').Groups[1].Value.Trim()
if (-not $seccion) { throw 'La sección "Sin publicar" del CHANGELOG está vacía.' }
$changelog = $changelog -replace '## Sin publicar', "## Sin publicar`n`n## $Version ($fecha)"
[IO.File]::WriteAllText("$raiz\CHANGELOG.md", $changelog, $utf8)

# 3. APK firmado
& "$raiz\gradlew.bat" assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación.' }
New-Item -ItemType Directory -Force "$raiz\release" | Out-Null
$apk = "$raiz\release\Camstyling-v$Version.apk"
Copy-Item "$raiz\app\build\outputs\apk\release\app-release.apk" $apk -Force

# 4. Git y Release de GitHub. Notas: la entrada del DEVLOG (si hay) y la del CHANGELOG.
# git escribe su avance en stderr; con 'Stop', PowerShell 5.1 lo tomaría como error.
$ErrorActionPreference = 'Continue'
git add gradle.properties CHANGELOG.md
git commit -q -m "Versión $Version"
git tag -a "v$Version" -m "Versión $Version"
git push -q origin HEAD "v$Version" 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Falló git push.' }
$ErrorActionPreference = 'Stop'

$devlog = [IO.File]::ReadAllText("$raiz\DEVLOG.md", $utf8)
$nota = [regex]::Match($devlog, "(?s)## $([regex]::Escape($Version)) [^\n]*\n(.*?)(?=\r?\n## |\z)").Groups[1].Value.Trim()
$notas = if ($nota) { "$nota`n`n## Cambios`n`n$seccion" } else { $seccion }
$archivoNotas = New-TemporaryFile
[IO.File]::WriteAllText($archivoNotas, $notas, $utf8)
gh release create "v$Version" $apk --title "Camstyling v$Version" --notes-file $archivoNotas
Remove-Item $archivoNotas

Write-Host "`nListo: $apk"
