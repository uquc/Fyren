# Fyren ECS Deploy — update JAR + Caddyfile + restart services
# Usage: powershell -ExecutionPolicy Bypass -File ecs-deploy.ps1
# Prerequisites: new JAR (Fyren-1.0-SNAPSHOT.jar) and Caddyfile in same directory

$ErrorActionPreference = "Continue"
$D = "C:\Fyren"

Write-Host "=== Fyren ECS Deploy ===" -ForegroundColor Cyan

# ---- 0. Check new JAR exists ----
if (-not (Test-Path "Fyren-1.0-SNAPSHOT.jar")) {
    Write-Host "ERROR: Fyren-1.0-SNAPSHOT.jar not found in current directory!" -ForegroundColor Red
    Write-Host "Copy the new JAR here first, then re-run." -ForegroundColor Red
    exit 1
}
Write-Host "[0/5] New JAR found: $(Get-Item Fyren-1.0-SNAPSHOT.jar).Length bytes" -ForegroundColor Green

# ---- 1. Stop services ----
Write-Host "[1/5] Stopping services..." -ForegroundColor Yellow
schtasks /end /tn "FyrenServer" 2>$null
schtasks /end /tn "FyrenCaddy" 2>$null
Start-Sleep 2
Get-Process java   -ErrorAction SilentlyContinue | Stop-Process -Force
Get-Process caddy  -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep 2
Write-Host "  Stopped." -ForegroundColor Green

# ---- 2. Backup old JAR ----
Write-Host "[2/5] Backing up old JAR..." -ForegroundColor Yellow
$backupName = "Fyren-1.0-SNAPSHOT.jar.bak." + (Get-Date -Format "yyyyMMdd-HHmmss")
if (Test-Path "$D\Fyren-1.0-SNAPSHOT.jar") {
    Copy-Item "$D\Fyren-1.0-SNAPSHOT.jar" "$D\$backupName"
    Write-Host "  Backed up to: $backupName" -ForegroundColor Green
} else {
    Write-Host "  No old JAR found, skipping backup." -ForegroundColor Yellow
}

# ---- 3. Deploy new JAR ----
Write-Host "[3/5] Deploying new JAR..." -ForegroundColor Yellow
Copy-Item -Force "Fyren-1.0-SNAPSHOT.jar" "$D\Fyren-1.0-SNAPSHOT.jar"
Write-Host "  Copied to $D\Fyren-1.0-SNAPSHOT.jar" -ForegroundColor Green

# ---- 4. Update Caddyfile ----
if (Test-Path "Caddyfile") {
    Write-Host "[4/5] Updating Caddyfile..." -ForegroundColor Yellow
    Copy-Item -Force "Caddyfile" "$D\caddy\Caddyfile"
    Write-Host "  Copied to $D\caddy\Caddyfile" -ForegroundColor Green
} else {
    Write-Host "[4/5] No Caddyfile in deploy package, skipping." -ForegroundColor Yellow
}

# ---- 5. Start services ----
Write-Host "[5/5] Starting services..." -ForegroundColor Yellow
schtasks /run /tn "FyrenServer" 2>$null
schtasks /run /tn "FyrenCaddy" 2>$null
Start-Sleep 4

# ---- Verify ----
Write-Host ""
Write-Host "=== Verify ===" -ForegroundColor Cyan
tasklist /fi "IMAGENAME eq java.exe" 2>NUL | findstr /I "java"
tasklist /fi "IMAGENAME eq caddy.exe" 2>NUL | findstr /I "caddy"
Write-Host ""
Write-Host "Check logs:" -ForegroundColor White
Write-Host "  type $D\logs\server-stdout.log"
Write-Host ""
Write-Host "=== Deploy Complete ===" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor White
Write-Host "  1. Check: Invoke-WebRequest http://localhost:8080/status"
Write-Host "  2. Check: Invoke-WebRequest http://localhost:9878 (WebSocket should be listening)"
Write-Host "  3. Open ECS security group TCP 9878 (Alibaba Cloud console)"
