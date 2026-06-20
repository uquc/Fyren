# Fyren 格斗游戏服务器 Docker 镜像
# 构建: docker build -t fyren-server -f docker/Dockerfile .
# 运行: docker compose -f docker/docker-compose.yml up -d

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 复制 fat JAR
COPY target/Fyren-1.0-SNAPSHOT.jar /app/Fyren.jar

# 暴露端口
# 8080 — HTTP 状态 API
# 8081 — 认证 API
# 9876/udp — UDP 游戏端口
# 9878 — WebSocket (浏览器客户端)
EXPOSE 8080 8081 9876/udp 9878

# Alpine 需要非 root 用户运行
RUN addgroup -S fyren && adduser -S fyren -G fyren
USER fyren

ENTRYPOINT ["java", "-cp", "/app/Fyren.jar", "com.Fyren.GameMain", "server", "--daemon"]
