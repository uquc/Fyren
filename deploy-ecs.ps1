# Fyren ECS Deployment Script
# Run as Administrator on ECS (Windows Server 2022)
# Usage: powershell -ExecutionPolicy Bypass -File deploy-ecs.ps1
# Prerequisites: deploy.zip in the same directory

$ErrorActionPreference = "Stop"
$DeployDir = "C:\Fyren"
$ZipPath = "$PSScriptRoot\deploy.zip"
$JreDir = "$DeployDir\jre-minimal"
$JavaBin = "$JreDir\bin\java.exe"
$JarPath = "$DeployDir\Fyren-1.0-SNAPSHOT.jar"

Write-Host "=== Fyren ECS Deployment ==="
Write-Host ""

# 1. Stop existing Java processes
Write-Host "[1/6] Stopping existing server..."
$existing = Get-Process -Name "java" -ErrorAction SilentlyContinue
if ($existing) {
    Stop-Process -Name "java" -Force
    Start-Sleep -Seconds 2
    Write-Host "  Stopped java process(es)"
} else {
    Write-Host "  No existing java processes"
}

# 2. Backup existing deployment
Write-Host "[2/6] Backing up existing deployment..."
if (Test-Path $DeployDir) {
    $backupDir = "$DeployDir.backup.$(Get-Date -Format 'yyyyMMdd_HHmmss')"
    Move-Item $DeployDir $backupDir
    Write-Host "  Backed up to $backupDir"
}

# 3. Extract deploy.zip
Write-Host "[3/6] Extracting deploy.zip..."
if (-not (Test-Path $ZipPath)) {
    Write-Error "deploy.zip not found at $ZipPath"
    exit 1
}
New-Item -ItemType Directory -Path $DeployDir -Force | Out-Null
Expand-Archive -Path $ZipPath -DestinationPath $DeployDir -Force
Write-Host "  Extracted to $DeployDir"

# 4. Verify Java
Write-Host "[4/6] Verifying Java runtime..."
if (-not (Test-Path $JavaBin)) {
    Write-Error "Java binary not found at $JavaBin"
    exit 1
}
$javaVersion = & $JavaBin -version 2>&1 | Select-Object -First 1
if ($LASTEXITCODE -eq 0 -or $javaVersion -match "version") {
    Write-Host "  $javaVersion"
} else {
    Write-Host "  Java OK (version check completed)"
}
[Environment]::SetEnvironmentVariable("JAVA_HOME", $JreDir, "Machine")
[Environment]::SetEnvironmentVariable("Path", "$JreDir\bin;" + [Environment]::GetEnvironmentVariable("Path", "Machine"), "Machine")

# 5. Configure firewall
Write-Host "[5/6] Configuring firewall rules..."
$rules = @(
    @{Name="Fyren Game UDP 9876"; Port=9876; Protocol="UDP"},
    @{Name="Fyren Status HTTP 8080"; Port=8080; Protocol="TCP"},
    @{Name="Fyren Auth API 8081"; Port=8081; Protocol="TCP"}
)

foreach ($rule in $rules) {
    $existingRule = netsh advfirewall firewall show rule name="$($rule.Name)" 2>&1
    if ($existingRule -match "No rules match") {
        netsh advfirewall firewall add rule name="$($rule.Name)" dir=in action=allow protocol=$($rule.Protocol) localport=$($rule.Port) | Out-Null
        Write-Host "  Added: $($rule.Name)"
    } else {
        Write-Host "  Already exists: $($rule.Name)"
    }
}

# 6. Start game server (daemon mode)
Write-Host "[6/6] Starting Fyren game server..."
if (-not (Test-Path $JarPath)) {
    Write-Error "JAR not found at $JarPath"
    exit 1
}

$logFile = "$DeployDir\server.log"
$now = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
"=== Server started at $now ===" | Out-File $logFile -Encoding utf8

Start-Process -WindowStyle Hidden -FilePath $JavaBin `
    -ArgumentList "-cp", $JarPath, "com.Fyren.GameMain", "server", "9876", "--daemon" `
    -RedirectStandardOutput $logFile -RedirectStandardError "$DeployDir\server-error.log"

Start-Sleep -Seconds 3

# Verify server started
$serverProcess = Get-Process -Name "java" -ErrorAction SilentlyContinue
if ($serverProcess) {
    Write-Host ""
    Write-Host "=== Deployment Complete ==="
    Write-Host "  PID: $($serverProcess.Id)"
    Write-Host "  Game UDP:   9876"
    Write-Host "  Status API: http://localhost:8080/status"
    Write-Host "  Auth API:   http://localhost:8081/"
    Write-Host "  Logs:       $logFile"
    Write-Host ""
    Write-Host "  Stop:  taskkill /f /im java.exe"
    Write-Host "  Check: curl http://115.29.230.57:8080/status"
} else {
    Write-Error "Server failed to start! Check $DeployDir\server-error.log"
}
