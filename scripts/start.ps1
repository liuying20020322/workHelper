param([switch]$NoBrowser)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$appProcess = $null
$outReader = $null
$errReader = $null
function Show-Log($reader) {
    if ($reader) { while (-not $reader.EndOfStream) { Write-Host $reader.ReadLine() } }
}
try {
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Java 21 is required. Please install it and add java to PATH.' }
    $ErrorActionPreference = 'Continue'
    $javaVersion = (& java -version 2>&1 | Out-String)
    $ErrorActionPreference = 'Stop'
    if ($javaVersion -notmatch 'version "21[.\"]') { throw 'Please use Java 21 to run workHelper.' }
    if (-not (Test-Path -LiteralPath 'target/workHelper.jar')) { throw 'Missing target/workHelper.jar. Run build.bat first.' }
    if (-not (Test-Path -LiteralPath 'src/main/resources/application.yml')) { throw 'Configure MySQL in src/main/resources/application.yml first.' }
    $appPort = '8080'
    $inServer = $false
    foreach ($line in Get-Content 'src/main/resources/application.yml') {
        if ($line -match '^server:\s*(#.*)?$') { $inServer = $true; continue }
        if ($line -match '^\S' -and $line -notmatch '^#') { $inServer = $false }
        if ($inServer -and $line -match '^  port:\s*([0-9]+)\s*(#.*)?$') { $appPort = $Matches[1] }
        elseif ($inServer -and $line -match '^  port:') { throw 'server.port must be an integer in src/main/resources/application.yml.' }
    }
    if ($appPort -notmatch '^\d+$' -or [int]$appPort -lt 1 -or [int]$appPort -gt 65535) { throw 'Invalid application port.' }
    $url = "http://127.0.0.1:$appPort"
    $existing = $false
    try {
        $info = Invoke-RestMethod "$url/api/info" -TimeoutSec 2
        $health = Invoke-RestMethod "$url/actuator/health" -TimeoutSec 2
        $existing = $info.application -eq 'workHelper' -and $health.status -eq 'UP'
    } catch { }
    if ($existing) {
        Write-Host "workHelper is already running at $url"
        if (-not $NoBrowser) { Start-Process "$url/#/applications" }
        exit 0
    }
    $probe = New-Object System.Net.Sockets.TcpClient
    try {
        $attempt = $probe.ConnectAsync('127.0.0.1', [int]$appPort)
        try { $null = $attempt.Wait(1000) } catch { }
        if ($probe.Connected) { throw "Port $appPort is already in use or workHelper is not healthy. Check the existing service or change server.port." }
    } finally { $probe.Dispose() }
    New-Item -ItemType Directory -Force logs | Out-Null
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    $outPath = Join-Path (Get-Location) "logs/$stamp.out.log"
    $errPath = Join-Path (Get-Location) "logs/$stamp.err.log"
    $appProcess = Start-Process java -WindowStyle Hidden -PassThru -ArgumentList @('-jar', 'target/workHelper.jar', '--spring.config.location=file:./src/main/resources/application.yml', "--server.port=$appPort") -RedirectStandardOutput $outPath -RedirectStandardError $errPath
    $outReader = [System.IO.StreamReader]::new([System.IO.File]::Open($outPath, 'Open', 'Read', 'ReadWrite'))
    $errReader = [System.IO.StreamReader]::new([System.IO.File]::Open($errPath, 'Open', 'Read', 'ReadWrite'))
    $deadline = (Get-Date).AddSeconds(90)
    $ready = $false
    Write-Host "Starting workHelper at $url (Ctrl+C to stop). Logs: $outPath"
    while (-not $appProcess.HasExited) {
        Show-Log $outReader
        Show-Log $errReader
        if (-not $ready) {
            try {
                $health = Invoke-RestMethod "$url/actuator/health" -TimeoutSec 2
                $info = Invoke-RestMethod "$url/api/info" -TimeoutSec 2
                $ready = $health.status -eq 'UP' -and $info.application -eq 'workHelper'
            } catch { }
            if ($ready) {
                Write-Host "Ready: $url/#/applications"
                if (-not $NoBrowser) { Start-Process "$url/#/applications" }
            } elseif ((Get-Date) -gt $deadline) { throw 'Startup timed out (90 seconds). Check MySQL service, database name, credentials and port in src/main/resources/application.yml.' }
        }
        Start-Sleep -Milliseconds 500
    }
    Show-Log $outReader
    Show-Log $errReader
    if (-not $ready -or $appProcess.ExitCode -ne 0) { throw 'Startup or service failed. Check MySQL connection / credentials and console errors above.' }
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
} finally {
    if ($appProcess -and -not $appProcess.HasExited) { Stop-Process -Id $appProcess.Id -ErrorAction SilentlyContinue }
    if ($outReader) { $outReader.Dispose() }
    if ($errReader) { $errReader.Dispose() }
}
