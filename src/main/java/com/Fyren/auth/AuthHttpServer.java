package com.Fyren.auth;

import com.Fyren.auth.middleware.AuthMiddleware;
import com.Fyren.auth.middleware.AuthMiddleware.AuthResult;
import com.Fyren.auth.model.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 认证 API 服务器（端口 8081）。
 *
 * 端点:
 *   POST /auth/register  — 注册
 *   POST /auth/login     — 登录
 *   POST /auth/refresh   — 刷新 Token
 *   POST /auth/logout    — 登出
 *   GET  /auth/me        — 获取当前用户信息
 *
 * 使用 com.sun.net.httpserver（与 HttpStatusServer 一致，零额外依赖）。
 */
public class AuthHttpServer {

    private final HttpServer server;
    private final AuthService authService;
    private final AuthMiddleware middleware;

    public AuthHttpServer(int port, AuthService authService, AuthMiddleware middleware) throws IOException {
        this.authService = authService;
        this.middleware = middleware;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);

        registerEndpoints();
        server.setExecutor(null); // 使用默认 executor
    }

    private void registerEndpoints() {
        server.createContext("/auth/register", new RegisterHandler());
        server.createContext("/auth/login", new LoginHandler());
        server.createContext("/auth/refresh", new RefreshHandler());
        server.createContext("/auth/logout", new LogoutHandler());
        server.createContext("/auth/me", new MeHandler());
    }

    public void start() {
        server.start();
        System.out.println("[AuthHttp] 认证 API 已启动，端口: " + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
    }

    // ==================== 端点处理器 ====================

    private class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"仅支持 POST\"}");
                return;
            }

            RegisterRequest req = parseBody(exchange, RegisterRequest.class);
            if (req == null) {
                sendJson(exchange, 400, "{\"error\":\"请求体格式错误\"}");
                return;
            }

            AuthService.RegisterResult result = authService.register(req);
            if (result.success) {
                String json = String.format("{\"userId\":%d,\"username\":\"%s\"}",
                        result.userId, escapeJson(result.username));
                sendJson(exchange, 201, json);
            } else {
                sendJson(exchange, 409, "{\"error\":\"" + escapeJson(result.errorMessage) + "\"}");
            }
        }
    }

    private class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"仅支持 POST\"}");
                return;
            }

            LoginRequest req = parseBody(exchange, LoginRequest.class);
            if (req == null) {
                sendJson(exchange, 400, "{\"error\":\"请求体格式错误\"}");
                return;
            }

            AuthService.LoginResult result = authService.login(req);
            if (result.success) {
                TokenResponse tr = result.tokenResponse;
                String json = String.format(
                    "{\"accessToken\":\"%s\",\"refreshToken\":\"%s\",\"userId\":%d,\"username\":\"%s\",\"mmr\":%d}",
                    tr.accessToken, tr.refreshToken, tr.userId, escapeJson(tr.username), tr.mmr);
                sendJson(exchange, 200, json);
            } else {
                sendJson(exchange, 401, "{\"error\":\"" + escapeJson(result.errorMessage) + "\"}");
            }
        }
    }

    private class RefreshHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"仅支持 POST\"}");
                return;
            }

            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            AuthService.RefreshResult result = authService.refresh(authHeader);
            if (result.success) {
                TokenResponse tr = result.tokenResponse;
                String json = String.format(
                    "{\"accessToken\":\"%s\",\"refreshToken\":\"%s\",\"userId\":%d,\"username\":\"%s\",\"mmr\":%d}",
                    tr.accessToken, tr.refreshToken, tr.userId, escapeJson(tr.username), tr.mmr);
                sendJson(exchange, 200, json);
            } else {
                sendJson(exchange, 401, "{\"error\":\"" + escapeJson(result.errorMessage) + "\"}");
            }
        }
    }

    private class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"仅支持 POST\"}");
                return;
            }

            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            AuthResult auth = middleware.authenticate(authHeader);
            if (!auth.success) {
                sendJson(exchange, auth.statusCode, "{\"error\":\"" + escapeJson(auth.errorMessage) + "\"}");
                return;
            }

            int userId = Integer.parseInt(auth.claims.getSubject());
            authService.logout(userId);
            sendJson(exchange, 200, "{\"message\":\"已登出\"}");
        }
    }

    private class MeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"仅支持 GET\"}");
                return;
            }

            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            AuthResult auth = middleware.authenticate(authHeader);
            if (!auth.success) {
                sendJson(exchange, auth.statusCode, "{\"error\":\"" + escapeJson(auth.errorMessage) + "\"}");
                return;
            }

            int userId = Integer.parseInt(auth.claims.getSubject());
            UserInfo info = authService.getUserInfo(userId);
            if (info == null) {
                sendJson(exchange, 404, "{\"error\":\"用户不存在\"}");
                return;
            }

            String json = String.format(
                "{\"userId\":%d,\"username\":\"%s\",\"mmr\":%d,\"role\":\"%s\"}",
                info.userId, escapeJson(info.username), info.mmr, escapeJson(info.role));
            sendJson(exchange, 200, json);
        }
    }

    // ==================== 工具方法 ====================

    /** 简易 JSON 体解析（不引入 Jackson/Gson，手动解析基本字段） */
    private <T> T parseBody(HttpExchange exchange, Class<T> clazz) throws IOException {
        InputStream is = exchange.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

        String username = extractJsonField(body, "username");
        String password = extractJsonField(body, "password");

        if (username == null || password == null) return null;

        if (clazz == LoginRequest.class) {
            return clazz.cast(new LoginRequest(username, password));
        } else if (clazz == RegisterRequest.class) {
            return clazz.cast(new RegisterRequest(username, password));
        }
        return null;
    }

    /** 从简易 JSON 字符串中提取字段值 */
    private String extractJsonField(String json, String fieldName) {
        // 匹配 "fieldName": "value" 或 "fieldName":"value"
        String pattern = "\"" + fieldName + "\"\\s*:\\s*\"";
        int startIdx = json.indexOf(pattern);
        if (startIdx < 0) {
            // 尝试匹配 pattern with proper escaping
            // Simple search for the field name pattern
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "\"" + java.util.regex.Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]*)\"");
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
            return null;
        }
        int valStart = startIdx + pattern.length();
        int valEnd = json.indexOf('"', valStart);
        if (valEnd < 0) return null;
        return json.substring(valStart, valEnd);
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
