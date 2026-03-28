param(
    [switch]$SkipBuild,
    [string]$JarPath = "lib/sqlite-jdbc-3.51.3.0.jar"
)

$ErrorActionPreference = "Stop"

if (-not $SkipBuild) {
    & "$PSScriptRoot/build.ps1" -JarPath $JarPath
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

if (-not (Test-Path $JarPath)) {
    throw "SQLite JDBC jar not found at '$JarPath'."
}

$classpath = "out;$JarPath"
Write-Host "Running tests.TestRunner with assertions enabled (-ea) ..."
& java -ea -cp $classpath tests.TestRunner
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
