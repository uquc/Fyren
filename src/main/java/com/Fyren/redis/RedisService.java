package com.Fyren.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.time.Duration;
import java.util.*;

/**
 * Redis 服务层 — 连接管理 + 用户/Token/在线状态操作封装。
 *
 * 无 Redis 可用时降级为内存模式（兼容开发/测试环境）。
 */
public class RedisService {

    private final String host;
    private final int port;
    private final String password;
    private JedisPool pool;
    private boolean available = false;

    // 内存降级存储
    private final Map<String, String> memoryStore = new HashMap<>();
    private final Map<String, Map<String, String>> memoryHashStore = new HashMap<>();
    private final Map<String, Set<String>> memorySetStore = new HashMap<>();
    private final Map<String, Map<String, Double>> memoryZSetStore = new HashMap<>();
    private int memoryIdCounter = 0;

    public RedisService() {
        this(
            System.getenv().getOrDefault("REDIS_HOST", "localhost"),
            Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379")),
            System.getenv().getOrDefault("REDIS_PASSWORD", "")
        );
    }

    public RedisService(String host, int port, String password) {
        this.host = host;
        this.port = port;
        this.password = password;
    }

    /** 初始化 Redis 连接池 */
    public void init() {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(10);
        config.setMaxIdle(5);
        config.setMinIdle(1);
        config.setTestOnBorrow(true);

        if (password != null && !password.isEmpty()) {
            pool = new JedisPool(config, host, port, 2000, password);
        } else {
            pool = new JedisPool(config, host, port, 2000);
        }

        try (Jedis jedis = pool.getResource()) {
            jedis.ping();
            available = true;
            System.out.println("[Redis] 已连接 " + host + ":" + port);
        } catch (JedisConnectionException e) {
            available = false;
            System.out.println("[Redis] 不可用 (" + e.getMessage() + ")，降级为内存模式");
        }
    }

    public boolean isAvailable() { return available; }

    public void close() {
        if (pool != null) pool.close();
        available = false;
    }

    // ==================== 通用操作 ====================

    private void set(String key, String value, long ttlSeconds) {
        if (available) {
            try (Jedis jedis = pool.getResource()) {
                if (ttlSeconds > 0) jedis.setex(key, ttlSeconds, value);
                else jedis.set(key, value);
            }
        } else {
            memoryStore.put(key, value);
        }
    }

    private String get(String key) {
        if (available) {
            try (Jedis jedis = pool.getResource()) {
                return jedis.get(key);
            }
        }
        return memoryStore.get(key);
    }

    private void del(String key) {
        if (available) {
            try (Jedis jedis = pool.getResource()) {
                jedis.del(key);
            }
        } else {
            memoryStore.remove(key);
        }
    }

    private boolean exists(String key) {
        if (available) {
            try (Jedis jedis = pool.getResource()) {
                return jedis.exists(key);
            }
        }
        return memoryStore.containsKey(key);
    }

    private void hset(String key, String field, String value) {
        if (available) {
            try (Jedis jedis = pool.getResource()) {
                jedis.hset(key, field, value);
            }
        } else {
            memoryHashStore.computeIfAbsent(key, k -> new HashMap<>()).put(field, value);
        }
    }

    private String hget(String key, String field) {
        if (available) {
            try (Jedis jedis = pool.getResource()) {
                return jedis.hget(key, field);
            }
        }
        Map<String, String> hash = memoryHashStore.get(key);
        return hash != null ? hash.get(field) : null;
    }

    private Map<String, String> hgetAll(String key) {
        if (available) {
            try (Jedis jedis = pool.getResource()) {
                return jedis.hgetAll(key);
            }
        }
        return memoryHashStore.getOrDefault(key, Collections.emptyMap());
    }

    private long incr(String key) {
        if (available) {
            try (Jedis jedis = pool.getResource()) {
                return jedis.incr(key);
            }
        }
        String val = memoryStore.get(key);
        long n = val != null ? Long.parseLong(val) + 1 : 1;
        memoryStore.put(key, String.valueOf(n));
        return n;
    }

    private void sadd(String key, String member) {
        if (available) {
            try (Jedis jedis = pool.getResource()) {
                jedis.sadd(key, member);
            }
        } else {
            memorySetStore.computeIfAbsent(key, k -> new HashSet<>()).add(member);
        }
    }

    private Set<String> smembers(String key) {
        if (available) {
            try (Jedis jedis = pool.getResource()) {
                return jedis.smembers(key);
            }
        }
        return memorySetStore.getOrDefault(key, Collections.emptySet());
    }

    private void srem(String key, String member) {
        if (available) {
            try (Jedis jedis = pool.getResource()) {
                jedis.srem(key, member);
            }
        } else {
            Set<String> set = memorySetStore.get(key);
            if (set != null) set.remove(member);
        }
    }

    private void zadd(String key, double score, String member) {
        if (available) {
            try (Jedis jedis = pool.getResource()) {
                jedis.zadd(key, score, member);
            }
        } else {
            memoryZSetStore.computeIfAbsent(key, k -> new HashMap<>()).put(member, score);
        }
    }

    private List<String> zrevrange(String key, long start, long stop) {
        if (available) {
            try (Jedis jedis = pool.getResource()) {
                return new ArrayList<>(jedis.zrevrange(key, start, stop));
            }
        }
        Map<String, Double> zset = memoryZSetStore.get(key);
        if (zset == null) return Collections.emptyList();
        return zset.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .skip(start)
                .limit(stop - start + 1)
                .map(Map.Entry::getKey)
                .toList();
    }

    // ==================== 用户操作 ====================

    /** 分配新用户ID */
    public int nextUserId() {
        return (int) incr("user:id:counter");
    }

    /** 保存用户 */
    public void saveUser(int userId, String username, String passwordHash, int mmr) {
        String key = "user:" + userId;
        hset(key, "userId", String.valueOf(userId));
        hset(key, "username", username);
        hset(key, "passwordHash", passwordHash);
        hset(key, "mmr", String.valueOf(mmr));
        hset(key, "createdAt", String.valueOf(System.currentTimeMillis()));
        hset(key, "role", "player");
        // 用户名唯一索引
        set("user:by:name:" + username, String.valueOf(userId), 0);
        // 排行榜
        updateLeaderboard(userId, mmr);
    }

    /** 通过用户名查找用户 */
    public Map<String, String> getUserByName(String username) {
        String userIdStr = get("user:by:name:" + username);
        if (userIdStr == null) return null;
        return getUserById(Integer.parseInt(userIdStr));
    }

    /** 通过ID查找用户 */
    public Map<String, String> getUserById(int userId) {
        Map<String, String> data = hgetAll("user:" + userId);
        return data.isEmpty() ? null : data;
    }

    /** 更新MMR */
    public void updateMmr(int userId, int newMmr) {
        hset("user:" + userId, "mmr", String.valueOf(newMmr));
        updateLeaderboard(userId, newMmr);
    }

    /** 用户名是否已存在 */
    public boolean usernameExists(String username) {
        return exists("user:by:name:" + username);
    }

    // ==================== Token 操作 ====================

    /** 检查 key 是否存在 */
    public boolean keyExists(String key) {
        return exists(key);
    }

    /** 保存 refresh token 到 Redis（key 存在 = token 有效，TTL 7天） */
    public void saveRefreshToken(int userId, String tokenId) {
        String key = "refresh:" + userId + ":" + tokenId;
        set(key, "1", 7 * 24 * 3600); // 7 天 TTL
        sadd("refresh:family:" + userId, tokenId);
    }

    /** 验证 refresh token 是否有效（未被撤销） */
    public boolean validateRefreshToken(int userId, String tokenId) {
        return exists("refresh:" + userId + ":" + tokenId);
    }

    /** 撤销指定 refresh token */
    public void invalidateRefreshToken(int userId, String tokenId) {
        del("refresh:" + userId + ":" + tokenId);
        srem("refresh:family:" + userId, tokenId);
    }

    /** 撤销用户所有 refresh token（登出所有设备） */
    public void invalidateAllRefreshTokens(int userId) {
        Set<String> tokenIds = smembers("refresh:family:" + userId);
        for (String tokenId : tokenIds) {
            del("refresh:" + userId + ":" + tokenId);
        }
        del("refresh:family:" + userId); // 清除集合
        if (!available) memorySetStore.remove("refresh:family:" + userId);
    }

    /** 将 access token jti 加入黑名单（登出后防止旧 token 重用） */
    public void blacklistAccessToken(String jti, long ttlSeconds) {
        set("blacklist:" + jti, "1", ttlSeconds);
    }

    /** 检查 access token 是否在黑名单 */
    public boolean isBlacklisted(String jti) {
        return exists("blacklist:" + jti);
    }

    // ==================== 在线状态 ====================

    /** 记录心跳 */
    public void heartbeat(int userId) {
        set("online:" + userId, String.valueOf(System.currentTimeMillis()), 30);
    }

    /** 获取在线玩家数 */
    public int getOnlineCount() {
        if (available) {
            try (Jedis jedis = pool.getResource()) {
                return (int) jedis.keys("online:*").size();
            }
        }
        // 内存模式：遍历并清理过期
        memoryStore.keySet().removeIf(k -> k.startsWith("online:"));
        return (int) memoryStore.keySet().stream().filter(k -> k.startsWith("online:")).count();
    }

    // ==================== 排行榜 ====================

    private void updateLeaderboard(int userId, int mmr) {
        zadd("mmr:leaderboard", mmr, String.valueOf(userId));
    }

    /** 获取 MMR 排行榜 Top N */
    public List<Map.Entry<String, Integer>> getLeaderboard(int topN) {
        List<String> members = zrevrange("mmr:leaderboard", 0, topN - 1);
        List<Map.Entry<String, Integer>> result = new ArrayList<>();
        for (String userIdStr : members) {
            Map<String, String> user = getUserById(Integer.parseInt(userIdStr));
            if (user != null) {
                int mmr = Integer.parseInt(user.getOrDefault("mmr", "1000"));
                String username = user.getOrDefault("username", "unknown");
                result.add(new AbstractMap.SimpleEntry<>(username, mmr));
            }
        }
        return result;
    }
}
