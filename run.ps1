param(
    [switch]$SkipBuild,
    [string]$MainClass = "Implementation.App",
    [string]$JarPath = "Autumn/lib/sqlite-jdbc-3.51.3.0.jar",
    [string]$JavaRelease = "25"
)

$ErrorActionPreference = "Stop"

if (-not $SkipBuild) {
    & "$PSScriptRoot/build.ps1" -JavaRelease $JavaRelease -JarPath $JarPath
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

if (-not (Test-Path $JarPath)) {
    throw "SQLite JDBC jar not found at '$JarPath'."
}

$classpath = "out;$JarPath"
Write-Host "Starting $MainClass ..."
& java -cp $classpath $MainClass
