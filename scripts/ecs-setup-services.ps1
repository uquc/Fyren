# Fyren ECS Server Setup — NSSM Windows Service
# Run as Administrator in PowerShell:  powershell -ExecutionPolicy Bypass -File ecs-setup-services.ps1

$FYREN_DIR = "C:\Fyren"
$JAVA_HOME  = "$FYREN_DIR\jre-minimal"
$NSSM_URL   = "https://nssm.cc/release/nssm-2.24.zip"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Fyren ECS Server Setup — NSSM Services" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# ---- 1. Stop old processes ----
Write-Host "[1/5] Stopping old processes..." -ForegroundColor Yellow
& nssm stop FyrenServer 2>$null
& nssm stop FyrenCaddy 2>$null
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
Get-Process caddy -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep 2

# ---- 2. Download NSSM ----
if (-not (Test-Path "$FYREN_DIR\nssm.exe")) {
    Write-Host "[2/5] Downloading NSSM..." -ForegroundColor Yellow
    Invoke-WebRequest -Uri $NSSM_URL -OutFile "$env:TEMP\nssm.zip"
    Expand-Archive -Path "$env:TEMP\nssm.zip" -DestinationPath "$env:TEMP\nssm" -Force
    Copy-Item "$env:TEMP\nssm\nssm-2.24\win64\nssm.exe" "$FYREN_DIR\nssm.exe" -Force
    Remove-Item "$env:TEMP\nssm.zip" -Force
    Remove-Item "$env:TEMP\nssm" -Recurse -Force
    Write-Host "  NSSM installed." -ForegroundColor Green
} else {
    Write-Host "[2/5] NSSM already installed." -ForegroundColor Green
}

$NSSM = "$FYREN_DIR\nssm.exe"

# ---- 3. Register Fyren Game Server service ----
Write-Host "[3/5] Registering FyrenServer service..." -ForegroundColor Yellow
& $NSSM remove FyrenServer confirm 2>$null
& $NSSM install FyrenServer "$JAVA_HOME\bin\java.exe" "-Djava.net.preferIPv4Stack=true -cp $FYREN_DIR\Fyren-1.0-SNAPSHOT.jar com.Fyren.GameMain server 9876 --daemon"
& $NSSM set FyrenServer AppDirectory "$FYREN_DIR"
& $NSSM set FyrenServer DisplayName "Fyren Game Server (UDP:9876 + WS:9878)"
& $NSSM set FyrenServer Description "Fyren 2D fighting game server"
& $NSSM set FyrenServer Start SERVICE_AUTO_START
& $NSSM set FyrenServer AppExit Default Restart
& $NSSM set FyrenServer AppRestartDelay 5000
& $NSSM set FyrenServer AppStdout "$FYREN_DIR\logs\server-stdout.log"
& $NSSM set FyrenServer AppStderr "$FYREN_DIR\logs\server-stderr.log"
& $NSSM set FyrenServer AppRotateFiles 1
& $NSSM set FyrenServer AppRotateSeconds 86400
& $NSSM set FyrenServer AppRotateBytes 10485760
Write-Host "  FyrenServer registered." -ForegroundColor Green

# ---- 4. Register Caddy HTTPS proxy service ----
Write-Host "[4/5] Registering FyrenCaddy service..." -ForegroundColor Yellow
& $NSSM remove FyrenCaddy confirm 2>$null
& $NSSM install FyrenCaddy "$FYREN_DIR\caddy\caddy.exe" "run --config $FYREN_DIR\caddy\Caddyfile"
& $NSSM set FyrenCaddy AppDirectory "$FYREN_DIR\caddy"
& $NSSM set FyrenCaddy DisplayName "Fyren Caddy HTTPS Proxy"
& $NSSM set FyrenCaddy Description "Caddy HTTPS reverse proxy"
& $NSSM set FyrenCaddy Start SERVICE_AUTO_START
& $NSSM set FyrenCaddy AppExit Default Restart
& $NSSM set FyrenCaddy AppRestartDelay 5000
& $NSSM set FyrenCaddy AppStdout "$FYREN_DIR\logs\caddy-stdout.log"
& $NSSM set FyrenCaddy AppStderr "$FYREN_DIR\logs\caddy-stderr.log"
& $NSSM set FyrenCaddy AppRotateFiles 1
& $NSSM set FyrenCaddy AppRotateSeconds 86400
& $NSSM set FyrenCaddy AppRotateBytes 10485760
Write-Host "  FyrenCaddy registered." -ForegroundColor Green

# ---- 5. Start services ----
Write-Host "[5/5] Starting services..." -ForegroundColor Yellow
New-Item -ItemType Directory -Force -Path "$FYREN_DIR\logs" | Out-Null
& $NSSM start FyrenServer
& $NSSM start FyrenCaddy

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Setup Complete!" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Services:" -ForegroundColor White
Write-Host "  FyrenServer  — UDP 9876, WS 9878, Auth 8081, Status 8080"
Write-Host "  FyrenCaddy   — HTTPS proxy (443 -> 8080)"
Write-Host ""
Write-Host "Management:" -ForegroundColor White
Write-Host "  nssm status FyrenServer"
Write-Host "  nssm restart FyrenServer"
Write-Host "  nssm status FyrenCaddy"
Write-Host "  nssm restart FyrenCaddy"
Write-Host ""
Write-Host "Logs:" -ForegroundColor White
Write-Host "  Get-Content $FYREN_DIR\logs\server-stdout.log -Tail 20"
Write-Host "  Get-Content $FYREN_DIR\logs\caddy-stdout.log -Tail 20"
