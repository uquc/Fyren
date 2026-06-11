# ============================================================
# ECS HTTPS Setup — Fyren
# Run as Administrator on ECS (115.29.230.57)
# Installs Caddy reverse proxy with auto Let's Encrypt cert
# ============================================================
$ErrorActionPreference = "Stop"
$Host.UI.RawUI.WindowTitle = "Fyren ECS HTTPS Setup"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Fyren ECS HTTPS Setup (Caddy + Let's Encrypt)" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# --- Admin check ---
if (-NOT ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole] "Administrator")) {
    Write-Host "[FATAL] Must run as Administrator! Right-click → Run as Administrator." -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

$CaddyDir = "C:\Fyren\caddy"
$CaddyExe = "$CaddyDir\caddy.exe"
$Caddyfile = "$CaddyDir\Caddyfile"
$Domain = "115.29.230.57.nip.io"

# --- Step 1: Download Caddy ---
Write-Host "[1/6] Downloading Caddy v2.7.6..." -ForegroundColor Yellow
New-Item -ItemType Directory -Force -Path $CaddyDir | Out-Null
$CaddyZip = "$CaddyDir\caddy.zip"
try {
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri "https://github.com/caddyserver/caddy/releases/download/v2.7.6/caddy_2.7.6_windows_amd64.zip" -OutFile $CaddyZip -UseBasicParsing
} catch {
    Write-Host "[FATAL] Failed to download Caddy: $_" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}
Expand-Archive -Path $CaddyZip -DestinationPath $CaddyDir -Force
Remove-Item $CaddyZip
Write-Host "       Caddy installed: $CaddyExe" -ForegroundColor Green
& $CaddyExe version

# --- Step 2: Create Caddyfile ---
Write-Host "[2/6] Creating Caddyfile..." -ForegroundColor Yellow
$CaddyConfig = @"
# Fyren HTTPS Reverse Proxy
# Domain: $Domain → ECS: 115.29.230.57
# Caddy auto-provisions Let's Encrypt cert via TLS-ALPN-01 challenge (port 443)
# Port 80 is occupied by IIS, so we use TLS-ALPN-01 instead of HTTP-01

$Domain {
    # Status API → localhost:8080
    handle_path /status* {
        reverse_proxy localhost:8080
    }

    # Leaderboard → localhost:8080
    handle_path /leaderboard* {
        reverse_proxy localhost:8080
    }

    # Auth API endpoints → localhost:8081
    handle_path /api/auth/* {
        reverse_proxy localhost:8081
    }

    # Default: health check
    handle {
        respond "Fyren HTTPS OK" 200
    }

    log {
        output file C:\Fyren\caddy\access.log
    }
}
"@
Set-Content -Path $Caddyfile -Value $CaddyConfig -Encoding UTF8
Write-Host "       Caddyfile saved: $Caddyfile" -ForegroundColor Green

# --- Step 3: Open Windows Firewall ---
Write-Host "[3/6] Opening Windows Firewall port 443..." -ForegroundColor Yellow
$existingRule = Get-NetFirewallRule -DisplayName "Caddy HTTPS (443)" -ErrorAction SilentlyContinue
if (-not $existingRule) {
    New-NetFirewallRule -DisplayName "Caddy HTTPS (443)" -Direction Inbound -Protocol TCP -LocalPort 443 -Action Allow -Profile Any | Out-Null
    Write-Host "       Firewall rule created" -ForegroundColor Green
} else {
    Write-Host "       Firewall rule already exists" -ForegroundColor Gray
}

# --- Step 4: Test Caddy config ---
Write-Host "[4/6] Validating Caddy config..." -ForegroundColor Yellow
try {
    & $CaddyExe validate --config $Caddyfile 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "       Config valid!" -ForegroundColor Green
    } else {
        Write-Host "       Config validation output:" -ForegroundColor Yellow
        & $CaddyExe validate --config $Caddyfile 2>&1
    }
} catch {
    Write-Host "       Validation attempted, continuing..." -ForegroundColor Yellow
}

# --- Step 5: Remove old scheduled task if exists ---
$taskName = "Fyren-Caddy-HTTPS"
$existingTask = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
if ($existingTask) {
    Write-Host "[5/6] Removing old scheduled task..." -ForegroundColor Yellow
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false
}

# --- Step 6: Install as scheduled task (runs at system startup) ---
Write-Host "[6/6] Installing Caddy as scheduled task (auto-start)..." -ForegroundColor Yellow
$Action = New-ScheduledTaskAction -Execute $CaddyExe -Argument "run --config `"$Caddyfile`""
$Trigger = New-ScheduledTaskTrigger -AtStartup
$Settings = New-ScheduledTaskSettingsSet -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1)
$Principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
Register-ScheduledTask -TaskName $taskName -Action $Action -Trigger $Trigger -Settings $Settings -Principal $Principal -Force | Out-Null

# Start Caddy now
Write-Host "       Starting Caddy..." -ForegroundColor Yellow
Start-ScheduledTask -TaskName $taskName
Start-Sleep -Seconds 8

# Verify Caddy is running
$taskInfo = Get-ScheduledTaskInfo -TaskName $taskName
Write-Host "       Task state: $($taskInfo.LastTaskResult)" -ForegroundColor $(if ($taskInfo.LastTaskResult -eq 0) { "Green" } else { "Yellow" })

# --- Done ---
Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  HTTPS Setup Complete!" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Test commands (run after 30s for cert provisioning):" -ForegroundColor White
Write-Host "    curl https://$Domain/status" -ForegroundColor Cyan
Write-Host "    curl https://$Domain/leaderboard" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Caddy directory: $CaddyDir" -ForegroundColor White
Write-Host "  Access log:      $CaddyDir\access.log" -ForegroundColor White
Write-Host ""
Write-Host "  ⚠ IMPORTANT: Also add TCP 443 to Alibaba Cloud Security Group!" -ForegroundColor Yellow
Write-Host "     Security Group: sg-bp10gn3btvuodp9coge" -ForegroundColor Gray
Write-Host ""
Read-Host "Press Enter to exit"
