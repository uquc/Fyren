package com.Fyren.auth.middleware;

import com.Fyren.auth.JwtTokenProvider;
import io.jsonwebtoken.Claims;

/**
 * HTTP 请求鉴权中间件 — 从 Authorization header 提取并验证 Bearer token。
 */
public class AuthMiddleware {

    private final JwtTokenProvider jwtTokenProvider;

    public AuthMiddleware(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 从 HTTP Authorization header 验证 token。
     *
     * @param authHeader "Bearer <token>" 格式
     * @return 验证结果（claims + 错误信息）
     */
    public AuthResult authenticate(String authHeader) {

        if (authHeader == null || authHeader.isEmpty()) {
            return AuthResult.fail(401, "缺少 Authorization header");
        }

        if (!authHeader.startsWith("Bearer ")) {
            return AuthResult.fail(401, "Authorization header 格式错误（需要 Bearer token）");
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            return AuthResult.fail(401, "Token 为空");
        }

        Claims claims = jwtTokenProvider.parseTokenSafe(token);
        if (claims == null) {
            return AuthResult.fail(401, "Token 无效或已过期");
        }

        // 验证是 access token（不是 refresh token）
        String type = jwtTokenProvider.getTokenType(claims);
        if (!"access".equals(type)) {
            return AuthResult.fail(401, "需要 Access Token，而非 Refresh Token");
        }

        return AuthResult.success(claims);
    }

    /**
     * 验证 refresh token。
     */
    public AuthResult authenticateRefreshToken(String authHeader) {
        AuthResult result = authenticate(authHeader);
        if (!result.success) return result;

        String type = jwtTokenProvider.getTokenType(result.claims);
        if (!"refresh".equals(type)) {
            return AuthResult.fail(401, "需要 Refresh Token");
        }

        return result;
    }

    /**
     * 鉴权结果。
     */
    public static class AuthResult {
        public final boolean success;
        public final Claims claims;
        public final int statusCode;
        public final String errorMessage;

        private AuthResult(boolean success, Claims claims, int statusCode, String errorMessage) {
            this.success = success;
            this.claims = claims;
            this.statusCode = statusCode;
            this.errorMessage = errorMessage;
        }

        static AuthResult success(Claims claims) {
            return new AuthResult(true, claims, 200, null);
        }

        static AuthResult fail(int statusCode, String errorMessage) {
            return new AuthResult(false, null, statusCode, errorMessage);
        }
    }
}
