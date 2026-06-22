# Fyren ECS — Redis Docker 一次性配置脚本
# 在 ECS 上以 Administrator 运行一次即可
# Usage: powershell -ExecutionPolicy Bypass -File ecs-setup-redis.ps1

$ErrorActionPreference = "Continue"

Write-Host "=== Fyren Redis Setup ===" -ForegroundColor Cyan

# ---- 1. Check Docker ----
Write-Host "[1/3] Checking Docker..." -ForegroundColor Yellow
$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
    Write-Host "ERROR: Docker 未安装！" -ForegroundColor Red
    Write-Host "手动安装 Docker Desktop for Windows:" -ForegroundColor White
    Write-Host "  https://docs.docker.com/desktop/setup/install/windows-install/"
    Write-Host "安装后重新运行此脚本。"
    exit 1
}
Write-Host "  Docker found: $(docker --version)" -ForegroundColor Green

# ---- 2. Start Redis container ----
Write-Host "[2/3] Starting Redis container..." -ForegroundColor Yellow

# 停止旧容器 (if exists)
docker rm -f fyren-redis 2>$null

docker run -d `
  --name fyren-redis `
  --restart unless-stopped `
  -p 6379:6379 `
  redis:7-alpine `
  redis-server --appendonly yes --requirepass fyren_redis

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Redis 容器启动失败" -ForegroundColor Red
    exit 1
}

Write-Host "  Redis container started: fyren-redis" -ForegroundColor Green

# ---- 3. Verify ----
Write-Host "[3/3] Verifying Redis..." -ForegroundColor Yellow
Start-Sleep 3
$result = docker exec fyren-redis redis-cli -a fyren_redis ping 2>$null
if ($result -match "PONG") {
    Write-Host "  Redis PONG! 已就绪" -ForegroundColor Green
} else {
    Write-Host "  Redis 可能还在启动，检查日志: docker logs fyren-redis" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== Setup Complete ===" -ForegroundColor Green
Write-Host ""
Write-Host "验证:" -ForegroundColor White
Write-Host "  curl http://localhost:8080/status  # redisConnected 应为 true"
Write-Host ""
Write-Host "下一步: 重新部署 JAR 以触发重连 Redis"
Write-Host "  curl -X POST --data-binary @Fyren-1.0-SNAPSHOT.jar http://localhost:8081/admin/deploy"
