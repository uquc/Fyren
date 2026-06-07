package com.Fyren.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT 双 Token 提供者 — Access Token (15min) + Refresh Token (7d)。
 *
 * 签名算法: HMAC-SHA256。
 * Secret 来源: 环境变量 JWT_SECRET → 无则生成随机密钥（仅开发用，重启后所有 token 失效）。
 */
public class JwtTokenProvider {

    private static final long ACCESS_TOKEN_TTL_MS = 15 * 60 * 1000;       // 15 分钟
    private static final long REFRESH_TOKEN_TTL_MS = 7 * 24 * 3600 * 1000; // 7 天

    private final SecretKey secretKey;

    public JwtTokenProvider() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.isEmpty() || "change-me-in-production".equals(secret)) {
            // 开发/测试环境生成随机密钥
            secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
            System.out.println("[JWT] ⚠ 使用随机密钥（开发模式），重启后所有 token 失效");
        } else {
            secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            System.out.println("[JWT] 使用环境变量 JWT_SECRET");
        }
    }

    /** 仅用于测试：传入固定 secret */
    public JwtTokenProvider(String secret) {
        secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ==================== Access Token ====================

    public String generateAccessToken(int userId, String username, String role) {
        String jti = UUID.randomUUID().toString();
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ACCESS_TOKEN_TTL_MS);

        return Jwts.builder()
                .id(jti)
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    // ==================== Refresh Token ====================

    public String generateRefreshToken(int userId, String username) {
        String jti = UUID.randomUUID().toString();
        Date now = new Date();
        Date expiry = new Date(now.getTime() + REFRESH_TOKEN_TTL_MS);

        return Jwts.builder()
                .id(jti)
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    // ==================== 验证 ====================

    /** 验证并解析 token，无效则抛异常 */
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 安全解析 token（不抛异常，无效返回 null） */
    public Claims parseTokenSafe(String token) {
        try {
            return validateToken(token);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /** 从 token claims 中提取 userId */
    public int getUserId(Claims claims) {
        return Integer.parseInt(claims.getSubject());
    }

    /** 从 token claims 中提取 username */
    public String getUsername(Claims claims) {
        return claims.get("username", String.class);
    }

    /** 从 token claims 中提取 jti */
    public String getTokenId(Claims claims) {
        return claims.getId();
    }

    /** 从 token claims 中提取 type */
    public String getTokenType(Claims claims) {
        return claims.get("type", String.class);
    }

    /** 检查 token 是否已过期 */
    public boolean isExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }
}
