<#
    Single entry point for local work. Low-level compile stays in build.ps1.

    .\dev.ps1 run              # build + start server
    .\dev.ps1 run -SkipBuild
    .\dev.ps1 test
    .\dev.ps1 seed             # fill DB if empty
    .\dev.ps1 seed -Reset      # delete app.db, sync + seed
    .\dev.ps1 kill             # stop whatever listens on 8080
#>
param(
    [Parameter(Position = 0)]
    [ValidateSet('run', 'test', 'seed', 'kill')]
    [string]$Command = 'run',

    [switch]$SkipBuild,
    [string]$MainClass = 'Implementation.App',
    [string]$JarPath = 'Autumn/lib/sqlite-jdbc-3.51.3.0.jar',
    [string]$JavaRelease = '25',

    [switch]$Reset,
    [int]$Port = 8080,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

function Ensure-Classpath {
    if (-not (Test-Path $JarPath)) {
        throw "SQLite JDBC jar not found at '$JarPath'."
    }
    return "out;$JarPath"
}

switch ($Command) {
    'kill' {
        $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        if (-not $connections) {
            Write-Host "No listening process on port $Port."
            exit 0
        }
        $processIds = $connections | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($processId in $processIds) {
            try {
                if ($Force) {
                    Stop-Process -Id $processId -Force -ErrorAction Stop
                } else {
                    Stop-Process -Id $processId -ErrorAction Stop
                }
                Write-Host "Stopped PID $processId on port $Port."
            } catch {
                Write-Warning "Failed to stop PID $processId`: $_"
            }
        }
        exit 0
    }

    default {
        if (-not $SkipBuild) {
            & "$PSScriptRoot/build.ps1" -JavaRelease $JavaRelease -JarPath $JarPath
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        }
        $cp = Ensure-Classpath

        switch ($Command) {
            'run' {
                Write-Host "Starting $MainClass ..."
                & java -cp $cp $MainClass
            }
            'test' {
                Write-Host "Running TestRunner (-ea) ..."
                & java -ea -cp $cp Implementation.tests.TestRunner
                if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
            }
            'seed' {
                $javaArgs = @('Implementation.SeedDatabase')
                if ($Reset) {
                    $javaArgs += '--reset'
                    Write-Host "Reset DB + seed ..."
                } else {
                    Write-Host "Seed if empty ..."
                }
                & java -cp $cp @javaArgs
                if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
            }
        }
    }
}
