@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -Command "if (Test-Path -LiteralPath 'target/workHelper.jar') { try { $jarCheck = [System.IO.File]::Open('target/workHelper.jar', 'Open', 'ReadWrite', 'None'); $jarCheck.Dispose() } catch { Write-Host 'Cannot replace target/workHelper.jar. Stop the running workHelper service before building.'; exit 1 } }"
if errorlevel 1 exit /b 1
where node >nul 2>nul
if errorlevel 1 (
  echo Node.js is required for building. Install Node.js 22.12+ or 24 LTS.
  exit /b 1
)
pushd frontend
call npm ci
if errorlevel 1 (popd & exit /b 1)
call npm run build
if errorlevel 1 (popd & exit /b 1)
popd
call mvnw.cmd -B -ntp clean package
if errorlevel 1 (
  echo Build failed. Check Java 21, network and Maven configuration.
  exit /b 1
)
echo Built target\workHelper.jar. Configure MySQL then run start.bat.
