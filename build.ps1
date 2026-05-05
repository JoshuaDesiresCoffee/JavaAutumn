param(
    [string]$JavaRelease = "25",
    [string]$JarPath = "Autumn/lib/sqlite-jdbc-3.51.3.0.jar",
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

if ($Clean -and (Test-Path "out")) {
    Remove-Item "out" -Recurse -Force
}

if (-not (Test-Path $JarPath)) {
    throw "SQLite JDBC jar not found at '$JarPath'."
}

$javaFiles = Get-ChildItem -Path "." -Recurse -Filter "*.java" -File |
    Where-Object { $_.FullName -notmatch "\\out\\" } |
    ForEach-Object { $_.FullName }

if (-not $javaFiles -or $javaFiles.Count -eq 0) {
    throw "No Java source files found."
}

New-Item -ItemType Directory -Path "out" -Force | Out-Null

Write-Host "Compiling Java sources with --release $JavaRelease ..."
& javac --release $JavaRelease -cp $JarPath -d out @javaFiles

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "Build successful. Classes are in ./out"
