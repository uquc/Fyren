@echo off
REM === Fyren ECS Server Setup — NSSM Windows Service ===
REM Run as Administrator on ECS Windows Server

set FYREN_DIR=C:\Fyren
set JAVA_HOME=%FYREN_DIR%\jre-minimal
set NSSM_URL=https://nssm.cc/release/nssm-2.24.zip

echo.
echo ==========================================
echo   Fyren ECS Server Setup — NSSM Services
echo ==========================================
echo.

REM 1. Stop any existing processes
echo [1/5] Stopping existing services...
nssm stop FyrenServer 2>nul
nssm stop FyrenCaddy 2>nul
taskkill /f /im java.exe 2>nul
taskkill /f /im caddy.exe 2>nul
timeout /t 2 >nul

REM 2. Download NSSM if not present
if not exist "%FYREN_DIR%\nssm.exe" (
    echo [2/5] Downloading NSSM...
    powershell -Command "Invoke-WebRequest -Uri '%NSSM_URL%' -OutFile '%TEMP%\nssm.zip'"
    powershell -Command "Expand-Archive -Path '%TEMP%\nssm.zip' -DestinationPath '%TEMP%\nssm' -Force"
    copy /y "%TEMP%\nssm\nssm-2.24\win64\nssm.exe" "%FYREN_DIR%\nssm.exe"
    del /q "%TEMP%\nssm.zip"
    rmdir /s /q "%TEMP%\nssm"
) else (
    echo [2/5] NSSM already installed.
)

REM 3. Create/Update Fyren Game Server service
echo [3/5] Registering FyrenServer Windows Service...
nssm remove FyrenServer confirm 2>nul
nssm install FyrenServer "%JAVA_HOME%\bin\java.exe" "-Djava.net.preferIPv4Stack=true -cp %FYREN_DIR%\Fyren-1.0-SNAPSHOT.jar com.Fyren.GameMain server 9876 --daemon"
nssm set FyrenServer AppDirectory "%FYREN_DIR%"
nssm set FyrenServer DisplayName "Fyren Game Server (UDP:9876 + WS:9878)"
nssm set FyrenServer Description "Fyren 2D fighting game server — UDP, WebSocket, Auth API, Status API"
nssm set FyrenServer Start SERVICE_AUTO_START
nssm set FyrenServer AppExit Default Restart
nssm set FyrenServer AppRestartDelay 5000
nssm set FyrenServer AppStdout "%FYREN_DIR%\logs\server-stdout.log"
nssm set FyrenServer AppStderr "%FYREN_DIR%\logs\server-stderr.log"
nssm set FyrenServer AppRotateFiles 1
nssm set FyrenServer AppRotateSeconds 86400
nssm set FyrenServer AppRotateBytes 10485760
nssm set FyrenServer AppTimestampLog 1

REM 4. Create/Update Caddy HTTPS proxy service
echo [4/5] Registering FyrenCaddy Windows Service...
nssm remove FyrenCaddy confirm 2>nul
nssm install FyrenCaddy "%FYREN_DIR%\caddy\caddy.exe" "run --config %FYREN_DIR%\caddy\Caddyfile"
nssm set FyrenCaddy AppDirectory "%FYREN_DIR%\caddy"
nssm set FyrenCaddy DisplayName "Fyren Caddy HTTPS Proxy"
nssm set FyrenCaddy Description "Caddy HTTPS reverse proxy — Let's Encrypt TLS for ECS"
nssm set FyrenCaddy Start SERVICE_AUTO_START
nssm set FyrenCaddy AppExit Default Restart
nssm set FyrenCaddy AppRestartDelay 5000
nssm set FyrenCaddy AppStdout "%FYREN_DIR%\logs\caddy-stdout.log"
nssm set FyrenCaddy AppStderr "%FYREN_DIR%\logs\caddy-stderr.log"
nssm set FyrenCaddy AppRotateFiles 1
nssm set FyrenCaddy AppRotateSeconds 86400
nssm set FyrenCaddy AppRotateBytes 10485760

REM 5. Create logs dir and start services
echo [5/5] Starting services...
mkdir "%FYREN_DIR%\logs" 2>nul
nssm start FyrenServer
nssm start FyrenCaddy

echo.
echo ==========================================
echo   Setup Complete!
echo ==========================================
echo.
echo Services:
echo   FyrenServer  — UDP 9876, WS 9878, Auth 8081, Status 8080
echo   FyrenCaddy   — HTTPS proxy (443 -^> 8080)
echo.
echo Management:
echo   nssm status FyrenServer
echo   nssm restart FyrenServer
echo   nssm status FyrenCaddy
echo   nssm restart FyrenCaddy
echo.
echo Logs:
echo   type %FYREN_DIR%\logs\server-stdout.log
echo   type %FYREN_DIR%\logs\caddy-stdout.log
echo.
pause
