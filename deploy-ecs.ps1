# Fyren ECS Deployment Script
# Run on ECS (Windows Server 2022) after copying fyren-deploy.zip
# Usage: powershell -ExecutionPolicy Bypass -File deploy-ecs.ps1

$ErrorActionPreference = "Stop"
$DeployDir = "C:\Fyren"

Write-Host "=== Fyren ECS Deployment ==="

# 1. Extract
Write-Host "[1/4] Extracting..."
if (-not (Test-Path "fyren-deploy.zip")) {
    Write-Host "ERROR: fyren-deploy.zip not found. Copy it to the same directory first."
    exit 1
}
Expand-Archive -Path "fyren-deploy.zip" -DestinationPath $DeployDir -Force

# 2. Set JAVA_HOME
Write-Host "[2/4] Setting up Java..."
$javaHome = "$DeployDir\jre-minimal"
$javaExe = "$javaHome\bin\java.exe"
if (-not (Test-Path $javaExe)) {
    Write-Host "ERROR: java.exe not found at $javaExe"
    exit 1
}
[Environment]::SetEnvironmentVariable("JAVA_HOME", $javaHome, "Machine")
[Environment]::SetEnvironmentVariable("Path", "$javaHome\bin;" + [Environment]::GetEnvironmentVariable("Path", "Machine"), "Machine")
& $javaExe -version

# 3. Firewall rules
Write-Host "[3/4] Configuring firewall..."
netsh advfirewall firewall add rule name="Fyren UDP 9876" dir=in action=allow protocol=UDP localport=9876 2>$null
netsh advfirewall firewall add rule name="Fyren HTTP 8080" dir=in action=allow protocol=TCP localport=8080 2>$null
netsh advfirewall firewall add rule name="Fyren HTTP 80" dir=in action=allow protocol=TCP localport=80 2>$null
Write-Host "Firewall rules added"

# 4. Verify
Write-Host "[4/4] Verification..."
Get-ChildItem $DeployDir | ForEach-Object { Write-Host "  $($_.Name) ($([math]::Round($_.Length/1MB, 1)) MB)" }

Write-Host ""
Write-Host "=== Deploy Complete ==="
Write-Host "Start server:"
Write-Host "  cd $DeployDir"
Write-Host "  .\jre-minimal\bin\java -cp Fyren-1.0-SNAPSHOT.jar com.Fyren.GameMain server 9876"
Write-Host ""
Write-Host "Test: curl http://115.29.230.57:8080/status"
