package com.Fyren.auth;

import com.Fyren.auth.model.LoginRequest;
import com.Fyren.auth.model.RegisterRequest;
import com.Fyren.auth.model.TokenResponse;
import com.Fyren.auth.model.UserInfo;
import com.Fyren.redis.RedisService;
import io.jsonwebtoken.Claims;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Map;
import java.util.UUID;

/**
 * 认证服务 — 注册/登录/刷新/登出业务逻辑。
 */
public class AuthService {

    private static final int DEFAULT_MMR = 1000;
    private static final String DEFAULT_ROLE = "player";

    private final RedisService redis;
    private final JwtTokenProvider jwt;

    public AuthService(RedisService redis, JwtTokenProvider jwt) {
        this.redis = redis;
        this.jwt = jwt;
    }

    // ==================== 注册 ====================

    public RegisterResult register(RegisterRequest req) {
        if (req.username == null || req.username.trim().isEmpty()) {
            return RegisterResult.fail("用户名不能为空");
        }
        if (req.password == null || req.password.length() < 6) {
            return RegisterResult.fail("密码至少需要6个字符");
        }

        String username = req.username.trim();

        if (redis.usernameExists(username)) {
            return RegisterResult.fail("用户名已存在");
        }

        int userId = redis.nextUserId();
        String passwordHash = BCrypt.hashpw(req.password, BCrypt.gensalt());

        redis.saveUser(userId, username, passwordHash, DEFAULT_MMR);

        System.out.printf("[Auth] 新用户注册: id=%d, username=%s\n", userId, username);
        return RegisterResult.success(userId, username);
    }

    // ==================== 登录 ====================

    public LoginResult login(LoginRequest req) {
        if (req.username == null || req.password == null) {
            return LoginResult.fail("用户名和密码不能为空");
        }

        String username = req.username.trim();
        Map<String, String> user = redis.getUserByName(username);
        if (user == null) {
            return LoginResult.fail("用户名或密码错误");
        }

        String storedHash = user.get("passwordHash");
        if (storedHash == null || !BCrypt.checkpw(req.password, storedHash)) {
            return LoginResult.fail("用户名或密码错误");
        }

        int userId = Integer.parseInt(user.get("userId"));
        int mmr = Integer.parseInt(user.getOrDefault("mmr", String.valueOf(DEFAULT_MMR)));
        String role = user.getOrDefault("role", DEFAULT_ROLE);

        // 生成 token 对
        String accessToken = jwt.generateAccessToken(userId, username, role);
        String refreshToken = jwt.generateRefreshToken(userId, username);

        // 存储 refresh token（key 存在 = 有效）
        Claims refreshClaims = jwt.validateToken(refreshToken);
        String tokenId = jwt.getTokenId(refreshClaims);
        redis.saveRefreshToken(userId, tokenId);

        redis.heartbeat(userId);

        System.out.printf("[Auth] 用户登录: id=%d, username=%s\n", userId, username);
        return LoginResult.success(new TokenResponse(accessToken, refreshToken, userId, username, mmr));
    }

    // ==================== 刷新 Token ====================

    public RefreshResult refresh(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return RefreshResult.fail("缺少 Refresh Token");
        }

        String token = authHeader.substring(7).trim();
        Claims claims = jwt.parseTokenSafe(token);
        if (claims == null) {
            return RefreshResult.fail("Refresh Token 无效或已过期");
        }

        if (!"refresh".equals(jwt.getTokenType(claims))) {
            return RefreshResult.fail("需要 Refresh Token");
        }

        int userId = jwt.getUserId(claims);
        String username = jwt.getUsername(claims);
        String oldTokenId = jwt.getTokenId(claims);

        // 检查旧 token 是否还在 Redis 中（未被撤销）
        if (!redis.validateRefreshToken(userId, oldTokenId)) {
            return RefreshResult.fail("Refresh Token 已被撤销");
        }

        // 轮换：撤销旧 refresh token
        redis.invalidateRefreshToken(userId, oldTokenId);

        // 生成新 token 对
        Map<String, String> user = redis.getUserById(userId);
        String role = user != null ? user.getOrDefault("role", DEFAULT_ROLE) : DEFAULT_ROLE;
        int mmr = user != null ? Integer.parseInt(user.getOrDefault("mmr", String.valueOf(DEFAULT_MMR))) : DEFAULT_MMR;

        String newAccessToken = jwt.generateAccessToken(userId, username, role);
        String newRefreshToken = jwt.generateRefreshToken(userId, username);

        Claims newRefreshClaims = jwt.validateToken(newRefreshToken);
        String newTokenId = jwt.getTokenId(newRefreshClaims);
        redis.saveRefreshToken(userId, newTokenId);

        return RefreshResult.success(new TokenResponse(newAccessToken, newRefreshToken, userId, username, mmr));
    }

    // ==================== 登出 ====================

    public boolean logout(int userId) {
        redis.invalidateAllRefreshTokens(userId);
        System.out.printf("[Auth] 用户登出: id=%d\n", userId);
        return true;
    }

    // ==================== 用户信息 ====================

    public UserInfo getUserInfo(int userId) {
        Map<String, String> user = redis.getUserById(userId);
        if (user == null) return null;

        return new UserInfo(
            Integer.parseInt(user.get("userId")),
            user.get("username"),
            Integer.parseInt(user.getOrDefault("mmr", String.valueOf(DEFAULT_MMR))),
            user.getOrDefault("role", DEFAULT_ROLE)
        );
    }

    // ==================== 结果类型 ====================

    public static class RegisterResult {
        public final boolean success;
        public final int userId;
        public final String username;
        public final String errorMessage;

        private RegisterResult(boolean success, int userId, String username, String error) {
            this.success = success;
            this.userId = userId;
            this.username = username;
            this.errorMessage = error;
        }

        static RegisterResult success(int userId, String username) {
            return new RegisterResult(true, userId, username, null);
        }

        static RegisterResult fail(String error) {
            return new RegisterResult(false, 0, null, error);
        }
    }

    public static class LoginResult {
        public final boolean success;
        public final TokenResponse tokenResponse;
        public final String errorMessage;

        private LoginResult(boolean success, TokenResponse tr, String error) {
            this.success = success;
            this.tokenResponse = tr;
            this.errorMessage = error;
        }

        static LoginResult success(TokenResponse tr) {
            return new LoginResult(true, tr, null);
        }

        static LoginResult fail(String error) {
            return new LoginResult(false, null, error);
        }
    }

    public static class RefreshResult {
        public final boolean success;
        public final TokenResponse tokenResponse;
        public final String errorMessage;

        private RefreshResult(boolean success, TokenResponse tr, String error) {
            this.success = success;
            this.tokenResponse = tr;
            this.errorMessage = error;
        }

        static RefreshResult success(TokenResponse tr) {
            return new RefreshResult(true, tr, null);
        }

        static RefreshResult fail(String error) {
            return new RefreshResult(false, null, error);
        }
    }
}
