# 第三讲：匹配系统与 JWT 认证

> 源码文件：`Matchmaker.java`、`MatchManager.java`、`JwtTokenProvider.java`、`AuthService.java`、`RedisService.java`

---

## Part A：匹配系统

### 1. ELO 评分算法

Fyren 使用简化的 ELO 系统。每个玩家初始 1000 分，K 因子 = 32。

`MatchManager.java:177-194` — `reportMatchResult()`：

```java
public void reportMatchResult(int player1Id, int player2Id, int winnerId) {
    PlayerRating rating1 = playerRatings.get(player1Id);
    PlayerRating rating2 = playerRatings.get(player2Id);

    if (winnerId == -1) {
        // 平局 — 各算 0.5 胜
        rating1.updateRating(rating2, 0.5);
        rating2.updateRating(rating1, 0.5);
    } else if (winnerId == player1Id) {
        rating1.updateRating(rating2, 1.0);  // P1 胜
        rating2.updateRating(rating1, 0.0);  // P2 负
    } else {
        rating1.updateRating(rating2, 0.0);
        rating2.updateRating(rating1, 1.0);
    }
}
```

ELO 的核心公式（在 `PlayerRating.updateRating()` 中）：
```
expectedScore = 1 / (1 + 10^((opponentRating - myRating) / 400))
newRating = oldRating + K * (actualScore - expectedScore)
```

- 比你高 400 分的对手：你赢的概率 ≈ 9%，赢了加 29 分，输了只扣 3 分
- 同分段：预期各 50%，赢加 16 分，输扣 16 分
- 扩散因子 400 是 ELO 标准值，保证高分对低分的预测合理

**面试提示**：`PlayerRating` 类不在我读的文件中，但如果面试官追问具体实现，核心就是上面的公式。可以坦诚说这部分用了标准 ELO 实现。

### 2. 扩散窗口机制

`Matchmaker.java:20-25` — 匹配策略的核心：

```
扩散窗口公式：
  maxDiff = baseDiff + waitTime × expandRate
  = 50 + waitTime(秒) × 5
  上限 400
```

代码实现（`MatchEntry.getAllowedDiff()` 第 66-69 行）：

```java
public int getAllowedDiff() {
    long waitMs = System.currentTimeMillis() - enqueueTime;
    int diff = BASE_MMR_DIFF + (int)(waitMs / 1000) * MMR_DIFF_EXPAND_RATE;
    return Math.min(diff, MAX_MMR_DIFF);
}
```

**举例**：
| 等待时间 | 允许分差 | 说明 |
|---------|---------|------|
| 0 秒 | 50 | 刚进队列，只匹配极近的对手 |
| 10 秒 | 100 | 稍微放宽 |
| 30 秒 | 200 | 继续放宽 |
| 70 秒 | 400（上限） | 已到底，再久也不无限扩大 |

**为什么要有扩散窗口？** 核心矛盾：匹配质量 vs 等待时间。严格匹配 → 等太久；随便匹配 → 实力悬殊。扩散窗口是一个折中——让等待时间成为弹性变量。

### 3. 匹配流程

`Matchmaker.processMatches()` 每 1 秒执行一次（第 139-178 行）：

```java
// 1. 按 rating 排序（相邻的分数最接近）
entries.sort(Comparator.comparingInt(e -> e.rating));

// 2. 遍历所有相邻对
for (int i = 0; i < entries.size() - 1; i++) {
    for (int j = i + 1; j < entries.size(); j++) {
        int ratingDiff = Math.abs(a.rating - b.rating);
        int allowedDiff = Math.max(a.getAllowedDiff(), b.getAllowedDiff());

        // 3. 分数在允许范围内 + 不是最近对战过的对手
        if (ratingDiff <= allowedDiff && !isRecentOpponent(a.playerId, b.playerId)) {
            // 配对成功！
            matchQueue.remove(a); matchQueue.remove(b);
            addRecentOpponent(a.playerId, b.playerId);
            onMatchFound.onMatch(a, b);
            break;
        }
    }
}
```

**关键细节**：
- `isRecentOpponent()` 避免 60 秒内重复匹配同一对手
- 延迟清理（`scheduler.schedule(..., 60_000ms)`）而非定时扫描，轻量且精确

### 4. MatchManager — 传输层解耦

`MatchManager.java:39-49`：

```java
// 接口解耦：UDP 和 WebSocket 客户端通过不同实现发送匹配结果
@FunctionalInterface
public interface MatchResponseSender {
    void sendMatchResponse(int playerId, MatchResponsePacket response,
                           String opponentAddress, int opponentPort);
}
```

这就是简历里说的"通过 MatchResponseSender 接口解耦传输层"的具体实现。`GameServer` 注入两个不同的实现：
- UDP 客户端 → `server.sendReliableTo(response, session.address)`
- WebSocket 客户端 → `wsSession.send(response.serialize())`

---

## Part B：JWT 双 Token 认证

### 5. 为什么双 Token？

单 Token 方案的问题：
- Token 设短（15min）→ 用户频繁重新登录
- Token 设长（7 天）→ 泄露后无法撤销，危害时间长

双 Token 方案：
```
Access Token (15min)  — 高频使用，短有效期，泄露后影响窗口小
Refresh Token (7d)   — 低频使用（只在 access 过期时用），存在 Redis，可随时撤销
```

### 6. JWT 结构

`JwtTokenProvider.java:45-78`：

**Access Token 的 Payload：**
```json
{
  "jti": "随机UUID",        // 唯一ID，可加入黑名单
  "sub": "123",             // userId
  "username": "player1",
  "role": "player",
  "type": "access",         // 防止 access token 被当 refresh token 用
  "iat": 时间戳,
  "exp": 时间戳 + 15分钟
}
```

**Refresh Token 的 Payload：**
```json
{
  "jti": "随机UUID",
  "sub": "123",
  "username": "player1",
  "type": "refresh",        // 关键区分
  "iat": 时间戳,
  "exp": 时间戳 + 7天
}
```

签名算法：**HMAC-SHA256**。对称密钥，服务端持有。不依赖非对称密钥的 PKI 体系，适合单体服务。

**密钥管理**：
```java
String secret = System.getenv("JWT_SECRET");
if (secret == null || secret.isEmpty()) {
    secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256); // 随机密钥
} else {
    secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
}
```
生产环境通过 ECS 环境变量注入固定密钥，开发环境用随机密钥（重启失效，无伤大雅）。

### 7. Refresh Token 轮换（防重放）

`AuthService.refresh()`（第 94-133 行）— 每次刷新时：

```java
// 1. 验证旧 refresh token
Claims claims = jwt.parseTokenSafe(token);
if (!"refresh".equals(jwt.getTokenType(claims))) { ... }

// 2. 检查 Redis 中是否仍有效（未被撤销）
if (!redis.validateRefreshToken(userId, oldTokenId)) { ... }

// 3. 撤销旧 refresh token（关键安全步骤）
redis.invalidateRefreshToken(userId, oldTokenId);

// 4. 生成新的 token 对
String newAccessToken = jwt.generateAccessToken(...);
String newRefreshToken = jwt.generateRefreshToken(...);
redis.saveRefreshToken(userId, newTokenId);
```

**为什么旧 token 必须撤销？** 如果攻击者截获了一个 refresh token，这个轮换机制使得：
- 合法用户下次刷新时旧 token 被撤销
- 攻击者再使用旧 token → Redis 查不到 → 被拒绝
- 攻击窗口 = 15 分钟（access token 的有效期），而非 7 天

### 8. 登出与黑名单

```java
// 登出时：撤销所有 refresh token
public boolean logout(int userId) {
    redis.invalidateAllRefreshTokens(userId);
    return true;
}
```

Access token 虽然不能主动撤销（JWT 无状态），但有效期短（15min），登出后最多 15 分钟内仍可用——这是无状态 JWT 的固有权衡。

### 9. bcrypt 密码哈希

`AuthService.register()`（第 32-53 行）：

```java
String passwordHash = BCrypt.hashpw(req.password, BCrypt.gensalt());  // 10 轮 salt
redis.saveUser(userId, username, passwordHash, DEFAULT_MMR);
```

验证时：
```java
BCrypt.checkpw(req.password, storedHash);
```

**为什么选 bcrypt？** 
- 自带 salt（每次哈希结果不同，即使相同密码）
- 计算慢（10 轮 = 2^10 次迭代），暴力破解成本高
- 简单——一个方法调用，不需要配置

---

## Part C：Redis 降级内存模式

### 10. 设计动机

ECS 上 Redis 可能因为各种原因不可用（未启动、端口被封、内存不足）。如果 Redis 挂了整个服务就不能用，这是不合理的——匹配对战是核心功能，用户数据持久化是附加功能。

### 11. 实现方式

`RedisService.java:24-29` — 每个 Redis 操作都有两个分支：

```java
// 每个操作都是 if-available-else-memory 模式
private void set(String key, String value, long ttlSeconds) {
    if (available) {
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(key, ttlSeconds, value);      // 真实 Redis
        }
    } else {
        memoryStore.put(key, value);                   // 内存 Map
    }
}
```

`init()` 方法（第 46-67 行）中的降级触发：

```java
try (Jedis jedis = pool.getResource()) {
    jedis.ping();             // 尝试 ping
    available = true;         // 成功 → 标记可用
} catch (JedisConnectionException e) {
    available = false;        // 失败 → 降级
    System.out.println("[Redis] 不可用，降级为内存模式");
}
```

**降级覆盖的操作**：
- KV 操作：`set/get/del/exists/incr`
- Hash 操作：`hset/hget/hgetAll`（用户数据）
- Set 操作：`sadd/smembers/srem`（refresh token 家族）
- ZSet 操作：`zadd/zrevrange`（排行榜）
- TTL：内存模式下 **不做 TTL 清理**（已知局限，坦诚说明）

**代价**：进程重启数据丢失。但对于"开发环境"和"Redis 临时宕机"两个场景，核心服务可用的收益大于数据不可持久化的代价。

---

## 12. 面试追问 & 标准回答

### Q: ELO 的 K 因子为什么取 32？

> K 因子控制评分变化的幅度。K=32 是国际象棋新手/中级的常用值。K 越大，分数变化越快，对新玩家的真实水平收敛更快。K 小则稳定但收敛慢。32 的取值既不会让一局结果翻覆排名，也能在 ~20 局内定位到大致水平。

### Q: 扩散窗口上限 400 是怎么定的？

> 400 分意味着 90% 的预期胜率差。扩大到超过 400 分，对局质量会严重下降（强方碾压弱方）。400 是 ELO 标准扩散因子，业界通用。

### Q: 为什么用对称密钥（HMAC）而不是非对称（RSA/ECDSA）？

> 单体服务只有一方签发和验证，不需要公钥分发。HMAC-SHA256 比 RSA 快两个数量级，密钥也更短。非对称密钥的优势在多服务间零信任——Fyren 不需要。

### Q: Access Token 15 分钟够用吗？一局对战可能超过 15 分钟。

> Access Token 只在**匹配前**验证（进入匹配队列时）。匹配成功后到对战结束，不再需要 token。所以 15 分钟针对的是"排队等匹配"的时间窗口，而非整局对战时间。

### Q: Refresh Token 轮换中，如果旧 token 撤销后新 token 还没到客户端怎么办？

> 新 token 在撤销旧 token 之后、返回响应之前生成。如果网络中断导致客户端没收到响应，用户需要重新登录。这是一个已知的取舍——安全性优先于便利性。业界方案（如 Auth0）的做法类似。

### Q: Redis 内存降级模式有什么缺陷？

> 主要三个：(1) 进程重启数据全丢；(2) TTL 不自动清理，`online:*` 键会堆积（但有在线统计时手动清理）；(3) 多实例不共享内存存储。但这些都是降级场景下的可接受代价——优先保证对战功能可用。
