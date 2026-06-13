# Fyren ECS Setup - Windows Task Scheduler (zero external deps)
# Run as Administrator: powershell -ExecutionPolicy Bypass -File ecs-setup.ps1

$ErrorActionPreference = "Continue"
$D = "C:\Fyren"

Write-Host "=== Fyren ECS Setup ===" -ForegroundColor Cyan

# ---- Stop old processes ----
Write-Host "[1/4] Stopping old processes..." -ForegroundColor Yellow
Get-Process java   -ErrorAction SilentlyContinue | Stop-Process -Force
Get-Process caddy  -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep 2

# ---- Create logs dir ----
New-Item -ItemType Directory -Force -Path "$D\logs" | Out-Null

# ---- Create watchdog batch files ----
Write-Host "[2/4] Creating watchdog scripts..." -ForegroundColor Yellow

$watchdogServer = @"
@echo off
tasklist /fi "IMAGENAME eq java.exe" 2>NUL | find /I "java.exe">NUL
if %ERRORLEVEL% NEQ 0 (
    echo %DATE% %TIME% Starting FyrenServer...
    $D\jre-minimal\bin\java.exe "-Djava.net.preferIPv4Stack=true" -cp $D\Fyren-1.0-SNAPSHOT.jar com.Fyren.GameMain server 9876 --daemon >> $D\logs\server-stdout.log 2>&1
)
"@
[IO.File]::WriteAllLines("$D\watchdog-server.bat", $watchdogServer, [Text.Encoding]::ASCII)

$watchdogCaddy = @"
@echo off
tasklist /fi "IMAGENAME eq caddy.exe" 2>NUL | find /I "caddy.exe">NUL
if %ERRORLEVEL% NEQ 0 (
    echo %DATE% %TIME% Starting FyrenCaddy...
    $D\caddy\caddy.exe run --config $D\caddy\Caddyfile >> $D\logs\caddy-stdout.log 2>&1
)
"@
[IO.File]::WriteAllLines("$D\watchdog-caddy.bat", $watchdogCaddy, [Text.Encoding]::ASCII)

Write-Host "  OK" -ForegroundColor Green

# ---- Register scheduled tasks ----
Write-Host "[3/4] Registering scheduled tasks..." -ForegroundColor Yellow

schtasks /delete /tn "FyrenServer" /f 2>$null
schtasks /delete /tn "FyrenCaddy" /f 2>$null

schtasks /create /tn "FyrenServer" /tr "$D\watchdog-server.bat" /sc MINUTE /mo 5 /ru SYSTEM /rl HIGHEST /f
if ($LASTEXITCODE -eq 0) { Write-Host "  FyrenServer OK" -ForegroundColor Green }
else { Write-Host "  FyrenServer FAILED" -ForegroundColor Red }

schtasks /create /tn "FyrenCaddy" /tr "$D\watchdog-caddy.bat" /sc MINUTE /mo 5 /ru SYSTEM /rl HIGHEST /f
if ($LASTEXITCODE -eq 0) { Write-Host "  FyrenCaddy OK" -ForegroundColor Green }
else { Write-Host "  FyrenCaddy FAILED" -ForegroundColor Red }

# ---- Start now ----
Write-Host "[4/4] Starting now..." -ForegroundColor Yellow
schtasks /run /tn "FyrenServer" 2>$null
schtasks /run /tn "FyrenCaddy" 2>$null
Start-Sleep 4

# ---- Verify ----
Write-Host ""
Write-Host "=== Verify ===" -ForegroundColor Cyan
tasklist /fi "IMAGENAME eq java.exe"  | findstr /I "java"
tasklist /fi "IMAGENAME eq caddy.exe" | findstr /I "caddy"
Write-Host ""
Write-Host "Check logs:" -ForegroundColor White
Write-Host "  type $D\logs\server-stdout.log"
Write-Host ""
Write-Host "Done. Servers will auto-restart on crash or reboot." -ForegroundColor Green
