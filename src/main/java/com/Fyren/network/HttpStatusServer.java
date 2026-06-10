package com.Fyren.network;

import com.Fyren.redis.RedisService;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 轻量 HTTP 状态 API — 供网站 AJAX 查询服务端运行状态。
 *
 * 端点:
 *   GET /status  → {"online":true,"uptimeSeconds":...,"onlinePlayers":...}
 *   GET /health  → 200 OK（负载均衡/监控用）
 *
 * 所有响应添加 CORS 头，允许网页端跨域请求。
 */
public class HttpStatusServer {

    private final HttpServer server;
    private final Instant startTime;
    private RedisService redisService;

    // 可写计数（由 GameServer 更新）
    private final AtomicInteger onlinePlayers = new AtomicInteger(0);
    private final AtomicInteger activeMatches = new AtomicInteger(0);
    private final AtomicLong totalMatches = new AtomicLong(0);

    public HttpStatusServer(int port) throws IOException {
        startTime = Instant.now();
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/status", new StatusHandler());
        server.createContext("/health", exchange -> {
            addCors(exchange);
            exchange.sendResponseHeaders(200, -1);
        });
        server.createContext("/leaderboard", new LeaderboardHandler());
        server.setExecutor(null);
    }

    public void setRedisService(RedisService redis) {
        this.redisService = redis;
    }

    public void start() {
        server.start();
        System.out.println("[HttpStatus] HTTP 状态 API 已启动，端口: " + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
    }

    // === 计数更新（由 GameServer/MatchManager 调用） ===

    public void setOnlinePlayers(int count) { onlinePlayers.set(count); }
    public void incrementOnlinePlayers(int delta) { onlinePlayers.addAndGet(delta); }
    public void setActiveMatches(int count) { activeMatches.set(count); }
    public void incrementActiveMatches() { activeMatches.incrementAndGet(); }
    public void decrementActiveMatches() { activeMatches.decrementAndGet(); }
    public void incrementMatches() { totalMatches.incrementAndGet(); }

    // === 端点处理器 ===

    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 处理 CORS 预检请求
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            long uptime = Duration.between(startTime, Instant.now()).getSeconds();
            boolean redisOk = redisService != null && redisService.isAvailable();

            String json = String.format(
                "{\"online\":true," +
                "\"uptimeSeconds\":%d," +
                "\"onlinePlayers\":%d," +
                "\"activeMatches\":%d," +
                "\"totalMatches\":%d," +
                "\"redisConnected\":%b," +
                "\"version\":\"1.1\"}",
                uptime,
                onlinePlayers.get(),
                activeMatches.get(),
                totalMatches.get(),
                redisOk
            );

            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            addCors(exchange);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
    }

    private class LeaderboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (redisService == null || !redisService.isAvailable()) {
                String body = "{\"error\":\"Redis 不可用，无法获取排行榜\"}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                addCors(exchange);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(503, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
                return;
            }

            List<Map.Entry<String, Integer>> leaderboard = redisService.getLeaderboard(100);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < leaderboard.size(); i++) {
                if (i > 0) sb.append(",");
                Map.Entry<String, Integer> entry = leaderboard.get(i);
                sb.append(String.format("{\"rank\":%d,\"username\":\"%s\",\"mmr\":%d}",
                        i + 1,
                        entry.getKey().replace("\\", "\\\\").replace("\"", "\\\""),
                        entry.getValue()));
            }
            sb.append("]");

            byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
            addCors(exchange);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        }
    }

    private void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
    }
}
