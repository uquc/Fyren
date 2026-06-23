# Fyren — 从零构建的帧同步格斗游戏：技术全景

**Fyren** 是一个个人全栈技术验证项目 —— 2D 格斗游戏，核心网络栈、战斗引擎、渲染管线、认证系统全部从零自研，不依赖任何游戏引擎或中间件。Java 17 单一代码库同时编译到桌面原生 (libGDX/LWJGL3) 和浏览器 WebGL (GWT)，约 60 个源文件承载了帧同步网络对战、P2P UDP 打洞、JWT 鉴权、WebSocket 跨平台联机等完整功能链。

## 确定性战斗核心

格斗游戏的生命线是确定性 —— 两个客户端以相同输入在相同帧号运行，必须产生逐字节一致的状态。Fyren 的状态机设计确保这一点：

**动作状态机 (Fighter.java)**
每个角色的每个动作遵循严格的 `STARTUP → ACTIVE → RECOVERY → IDLE` 阶段机。受击 (STUN) 可在任意阶段中断当前动作，影 (KAGE) 特殊技可取消收招帧。所有状态转换仅依赖当前帧计数和输入指令，不使用 `Math.random()` 或浮点时间戳。

**帧数据驱动碰撞**
`CollisionSystem.java` 不从位置插值推断命中 —— 每一帧根据当前动作的帧数据表 (`startupFrames` / `activeFrames` / `recoveryFrames`) 判定是否在判定窗口，再结合受击框 (green hitbox) 和攻击框 (red attack box) 做 AABB 碰撞。判定逻辑覆盖七种场景：命中、格挡、投技破防、相杀（双方同时攻击各受半伤+短硬直）、空振、硬直打断、分离状态。

**快照与回滚**
`GameStateSnapshot` 包含 40 个字段的完整战斗状态（位置、速度、HP、硬直计数器、帧进度、特殊资源、冲刺次数等），每 10 帧保存一次。回滚时恢复快照，以本地缓存输入 + 新到达的远程输入重新模拟到当前帧。回滚上限 10 帧，在预测精度和计算开销间取平衡。

**帧同步引擎 (FrameSyncManager.java)**
采用乐观帧锁定 (optimistic lockstep) 模型：不等远程输入到达就先行执行本地预测，后续每帧比对远程输入校验。若校验通过，快照向前滑动；若不一致，触发回滚。GWT 版本 (`GwtFrameSyncManager.java`) 将同步逻辑重写为主线程驱动，不依赖线程/锁（浏览器 JS 线程模型的硬约束）。

## 网络栈：UDP + WebSocket 双传输 + P2P 打洞

网络层是项目中最具工程深度的部分，从传输协议到包格式全部自研。

**自定义协议栈 (Packet.java)**
8 字节二进制包头 (`[type:2][playerId:4][sequence:2]`) + 变长负载，定义了 10 种包类型：`INPUT`、`STATE`、`HEARTBEAT`、`MATCH_REQ`、`MATCH_RES`、`ACK`、`RESULT`、`P2P_PING`、`P2P_PONG`。两种信道模型 —— 可靠信道 (ACK + 超时重传，类似 TCP 但更轻量) 和不可靠信道 (fire-and-forget，用于高频输入同步)。

**P2P UDP 打洞 (P2PHandshake.java)**
匹配成功后，双方各向对方公网地址发送 10 个 `P2P_PING` 包（间隔 20ms）打通 NAT。收到对方的 `P2P_PONG` 后激活直连通道。2 秒超时则自动降级为服务器中继。纯 Java 实现，无外部 STUN/TURN 依赖。对称 NAT 场景下自动回退中继，保证连通性。

**跨协议匹配池 (MatchManager.java)**
`MatchResponseSender` 接口解耦传输层 —— UDP 客户端走 `DatagramPacket` 回包，WebSocket 客户端走 `WsSession.send()`。同一匹配队列同时服务桌面端和浏览器端，两者可互相对战。防重复匹配逻辑使用 `HashSet<String>` 记录已匹配 pair。

**GWT WebSocket (GwtWebSocket.java)**
浏览器端通过 JSNI (JavaScript Native Interface) 封装浏览器原生 `WebSocket` API，以 `ArrayBuffer` 二进制帧与服务端通信。自动检测页面协议 —— HTTPS 下升级为 `wss://` 通过 Caddy nip.io TLS 代理连接到服务端 9878 端口。

## 匹配系统

**ELO + 扩散窗口**
`Matchmaker.java` 实现 ELO 评分匹配，等待超时时逐步扩大可匹配的评分范围（扩散窗口）。新玩家初始 1000 分，K 因子 32。每个匹配周期检查队列中所有候选对，在窗口内取最近 ELO 的一对。

**结果上报**
对局结束时客户端发送 `ResultPacket`（含双方 ID 和胜者 ID），服务端 `MatchManager.reportMatchResult()` 更新双方 MMR 并维护 `mmr:leaderboard` (Redis ZSet) 排行榜。

## 打击感系统

打击反馈不是事后加的特效层，而是深嵌在碰撞检测、状态机和渲染循环中的五层并行系统：

1. **命中停帧 (Hit-stop)** — 攻击命中/被防时冻结画面 4–8 帧，制造"打中了"的重量感
2. **屏幕震动 (Camera shake)** — `CameraController` 根据伤害量计算振幅和衰减
3. **粒子火花 (ParticleEffects)** — 命中点生成 8–12 个随机速度的橙色火花粒子，带重力衰减
4. **受击闪烁 (Damage flash)** — 受击角色白色闪烁 3 帧
5. **运动残影 (MotionTrailEffect)** — 冲刺/特殊技期间每 2 帧采样角色轮廓，形成半透明拖尾

## 角色系统

三名角色共享动作状态机，但通过 `FighterPreset` 注入差异化的帧数据、受击框尺寸和特殊机制：

| 角色 | 特殊资源 | 机制 |
|------|---------|------|
| 影 (KAGE) | 3 秒 CD | 特殊技可取消收招帧，主打压制和连续技 |
| 武 (TAKESHI) | 造成 40 伤害充能 | 累积伤害输出解锁特殊技，鼓励进攻 |
| 刚 (GOU) | 承受 50 伤害充能 | 累积受伤量解锁特殊技，鼓励防守反击 |

冲刺系统：双击方向键（200ms 窗口），3 次充能，3 秒回复一次。冲刺攻击启动帧减半、伤害 -5，带来高风险高回报的择边博弈。

## 跨平台渲染管线

**单一代码库，两个编译目标** —— 这是项目最具野心的工程决策之一。

**桌面端 (libGDX + LWJGL3)**
```
FyrenLauncher (CLI) → FyrenGame (ApplicationListener)
  → GameScreen
    ├── update: 输入采样 → 世界更新 → 碰撞检测 → 特效触发
    └── render: 视差背景 → SpriteBatch 角色 → 残影 → 粒子 → HUD
```

**浏览器端 (GWT → JavaScript)**
GWT 编译器将 Java 源码交叉编译为 5 个排列组合（不同浏览器优化）的 JavaScript，部署到 GitHub Pages。关键适配：
- `java.awt.Rectangle` → 自定义 `Rect`（GWT 没有 AWT）
- `Color.getRGB()` → `int` RGBA 打包
- `String.format()` → 手动拼接
- 多线程 → 主线程驱动的事件循环
- `Gdx.audio` → 静默降级

**结果：同一套 Java 战斗核心，桌面端 60fps 原生渲染 + P2P 直连，浏览器端免安装即开即玩 + WebSocket 中继。**

## 程序化内容生成

整个游戏的视觉内容完全由代码生成，无外部美术素材：

- **4 层视差背景** — 天空渐变 + 月亮 + 80 颗闪烁星点 (0.05x) → 双层远山剪影正弦叠加 (0.15x) → 60 棵竹/松树（竹有节+叶，松有锥形叠层）(0.4x) → 土色地面 + 草线 + 石块纹理 + 中线分隔 (1.0x)
- **角色精灵** — `SpriteRenderer` 运行时生成头部椭圆、躯干矩形、四肢多边形的程序化纹理，通过 `SpriteBatch` 批量绘制。每个 `FighterStance`（10 种姿态）有独立的肢体角度配置
- **音效** — `SoundGenerator.java` 程序化生成 6 个 WAV 文件（正弦波/噪音/扫频），覆盖轻击、重击、特殊技、冲刺、格挡、KO

## 认证与安全

**JWT 双 Token 机制 (AuthHttpServer.java, JwtTokenProvider.java)**
- Access Token: HMAC-SHA256 签名，15 分钟有效期
- Refresh Token: 7 天有效期，存储在 Redis（TTL 对齐），轮换时旧 Token 加入黑名单防重放
- bcrypt 密码哈希，10 轮 salt
- 匹配时服务端验证 JWT 签名、过期时间、`sub` 字段和 `type` 声明；UDP 客户端强制鉴权，WebSocket Demo 模式 (Guest) 豁免

**Redis 降级模式 (RedisService.java)**
Jedis 连接池不可用时自动切换内存 `ConcurrentHashMap` 存储，保证开发环境和 Redis 宕机时核心功能不中断。线上部署通过 ECS 环境变量注入 `REDIS_HOST`/`REDIS_PORT`/`JWT_SECRET`。

## 部署与运维

**ECS 架构** (阿里云 Windows Server 2022)
```
Internet
  ├── :443  Caddy (HTTPS + WSS 反代)
  ├── :9876 Game Server (UDP)
  ├── :9878 WebSocket Server
  ├── :8080 HTTP Status API (/status, /leaderboard)
  └── :8081 Auth HTTP API (注册/登录/刷新/热部署)
```

- **HTTP 热部署** — `POST /admin/deploy` 接收新 JAR 并自动备份 + 替换 + 重启进程，无需 RDP
- **进程守护** — Windows `schtasks` 每 5 分钟检查 `java` 进程，崩溃自动拉起
- **自动化部署脚本** — `scripts/ecs-deploy.ps1` 一条龙更新 JAR + Caddyfile + 重启服务
- **Docker 编排** — `docker-compose.yml` 编排 Redis 7 + Fyren 应用
- **Windows EXE 分发** — `jpackage --type app-image` 生成自包含目录（含 JRE），zip 后约 91MB

## 测试体系

33 条单元测试覆盖关键路径：`MatchRequestPacketTest` (JWT 序列化/向后兼容)、`CollisionSystemTest` (命中/格挡/投技/相杀)、`FighterActionTest` (状态机/STUN/特殊资源) 等。测试框架 JUnit 5。

## 技术债务与已知限制

- `float` 浮点坐标 — Java `strictfp` 保证同平台一致，但不保证跨架构确定性
- GWT 编译产物较大 (~6.8MB/排列)，音效在浏览器端静默降级
- 对称 NAT 下 P2P 不可用，但自动中继降级保证连通性
- Windows EXE 为 app-image 免安装目录（非 MSI/EXE 安装包），缺少 WiX toolset

---

*Fyren 的核心价值不在于画面或玩法，而在于它证明了一个开发者在不依赖现成引擎/中间件的情况下，可以走通「格斗游戏核心逻辑 → 延迟补偿网络 → 跨平台部署 → 认证与运维」的全链路。每一个模块都是从协议字节对齐写到像素着色，对于理解实时多人游戏的底层原理具有极高的参考价值。*
