# CLAUDE.md — [role: developer]

**Who I am:** 开发者 Claude。职责：实现功能、修复 Bug、重构、写业务代码。可以修改 `src/main/` 下任何文件。

**Counterpart:** [[tester-claude-md]] — 测试员 Claude 的独立指令集，存储于 memory/。

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build (JDK 17+, Maven 3.8+)
mvn compile -q
mvn test -q          # JUnit 5 configured, tests in src/test/

# Start server (with Redis, Auth API)
java -cp target/classes com.Fyren.GameMain server 9876

# Register / Login (auth API must be running on port 8081)
java -cp target/classes com.Fyren.GameMain register localhost <username> <password>
java -cp target/classes com.Fyren.GameMain login localhost <username> <password> --preset kage

# Start client (--preset kage|takeshi|gou, default takeshi)
java -cp target/classes com.Fyren.GameMain client <serverIp> [port] [playerId] --preset kage

# Local demo (libGDX window, dual keyboard)
java -cp target/classes com.Fyren.render.libgdx.FyrenLauncher demo --preset kage --preset2 gou

# EXE 联网对战 (v0.3.0+):
#   双击 Fyren.exe → TitleScreen → "NETWORK MATCH"
#   → NetworkSetupScreen: 输入服务器IP + 用户名 + 密码 → 登录/注册
#   → 选人 → MatchingScreen → 对战

# Fat JAR (shade plugin)
mvn package -q
java -jar target/Fyren-1.0-SNAPSHOT.jar            # libGDX demo
java -cp target/Fyren-1.0-SNAPSHOT.jar com.Fyren.GameMain server 9876
java -cp target/Fyren-1.0-SNAPSHOT.jar com.Fyren.GameMain client <ip> 9876 <id> --preset kage
java -cp target/Fyren-1.0-SNAPSHOT.jar com.Fyren.GameMain register localhost <user> <pass>
java -cp target/Fyren-1.0-SNAPSHOT.jar com.Fyren.GameMain login localhost <user> <pass> --preset kage

# Docker
cd docker && docker compose up -d

# GWT/WebGL compile (first time: download sources)
mvn dependency:sources -DincludeArtifactIds=gdx-backend-gwt,gdx -q
mvn compile -q && ./gwt-compile.bat   # output: target/gwt-out/

	# Windows EXE 分发 (jpackage app-image → zip，无需 WiX)
	mvn package -q
	jpackage --input target/pkg-input --name Fyren --main-jar Fyren-1.0-SNAPSHOT.jar --main-class com.Fyren.render.libgdx.FyrenLauncher --type app-image --java-options "-XstartOnFirstThread" --dest target/pkg-out
	# 注意: --type exe 需要 WiX toolset (light.exe/candle.exe) 且在当前环境不可用
	# 最终分发: zip target/pkg-out/Fyren/ → Fyren-windows-x64.zip (~91MB, 含 JRE)

Dependencies: Lombok (provided), JUnit Jupiter 5.10.2 (test), libGDX 1.12.1 (gdx, gdx-backend-lwjgl3, gdx-platform:natives-desktop), jjwt 0.12.5 (api/impl/jackson), Jedis 5.1.2, jBCrypt 0.4, java-websocket 1.5.6.

## Architecture

**Fyren** is a 2D fighting game with UDP networking, hidden-MMR matchmaking, lockstep + rollback frame sync, P2P UDP hole punch, and libGDX rendering. Java 17+, Maven, single jar for both client and server. ~58 source files.

```
com.Fyren
├── game/               (unchanged — deterministic core)
│   Fighter           — action state machine, frame data, dash, special resources, hitbox/attack-box
│   FighterPreset     — enum: KAGE(影)/TAKESHI(武)/GOU(刚), stats, hitbox sizes, visual props
│   FighterStance     — enum: IDLE/WALK/PUNCH/KICK/THROW/SPECIAL/BLOCK/HURT/DASH
│   GameWorld         — deterministic update loop, timer(99s), round-end, snapshots
│   CollisionSystem   — frame-data-aware hit detection, clash, throw-breaks-guard
│   GameStateSnapshot — full state snapshot (40 fields) for rollback restore
├── render/
│   ├── libgdx/           ★ new render layer (replaces Swing for prod)
│   │   FyrenGame         — ApplicationListener, lifecycle, demo/network mode dispatch
│   │   FyrenLauncher     — LWJGL3 Launcher, CLI arg parsing (--preset, --preset2)
│   │   GameScreen        — per-frame update + render orchestrator
│   │   GdxInputHandler   — Gdx.input polling, P1(WASD+JKLU) / P2(arrows+123)
│   │   CameraController  — dynamic camera, midpoint tracking, shake effect
│   │   SpriteRenderer    — procedural textures + SpriteBatch stick-figure rendering
│   │   HudRenderer       — health bars, timer, round indicators, "YOU WIN/LOSE"
│   │   HitEffects        — hit-stop (4-8 frames), white flash, knockback displacement
│   │   ParticleEffects   — hit sparks, KO explosion, dash dust
│   │   MotionTrailEffect — afterimage trail during dash/special
│   │   AudioManager      — 6 CC0 WAV sound effects (hit/special/dash/block/KO), libGDX Sound API
│   │   BackgroundRenderer — 4-layer parallax background (sky+stars+moon, mountains, bamboo/trees, ground+grass)
│   │   TrainingScreen     — solo practice mode, frame data overlay, input display, dummy opponent
│   │   └── gwt/
│   │       FyrenGwtLauncher — GWT/WebGL entry (demo + network mode via ?mode=network)
│   ├── SwingGameWindow   — JFrame 960×540, Swing Timer 16ms, for network client mode
│   ├── DemoGameWindow    — standalone dual-keyboard demo (no GameClient dependency)
│   ├── GamePanel         — JPanel paintComponent, world→screen coordinate mapping
│   ├── StickFigureRenderer — static Java2D stick-figure drawing per stance/preset
│   └── KeyInputHandler   — KeyListener with key-state bitset, double-tap dash detection, P1/P2 mappings
├── network/
│   Packet (abstract)     — 8-byte header, Type enum: INPUT/STATE/HEARTBEAT/MATCH_REQ/MATCH_RES/ACK/RESULT/P2P_PING/P2P_PONG
│   MatchRequestPacket    — playerId, rating, presetOrdinal
│   MatchResponsePacket   — opponentId, rating, address, opponentPresetOrdinal
│   ResultPacket          — player1Id, player2Id, winnerId (game result → MMR update)
│   P2PHandshake          — async NAT traversal (P2P_PING ×10 → wait P2P_PONG → 2s timeout → relay fallback)
│   P2pPacket             — minimal header-only packet for hole punching (no payload)
│   HttpStatusServer      — embedded HTTP server on port 8080, /status JSON endpoint
│   WsGameServer          — WebSocket server on 9878, browser clients, shared match pool
│   WsSession             — WebSocket conn state (mirrors UdpServer.ClientSession)
│   UdpClient, UdpServer  — reliable(ACK+retransmit) + unreliable(fire-and-forget) channels, P2P routing
│   └── gwt/
│       GwtWebSocket      — JSNI browser WebSocket wrapper (binary frames)
│       GwtNetworkClient  — GWT-compatible GameClient (WS transport, same state machine)
├── sync/
│   FrameSyncManager  — lockstep + speculative execution + rollback (max 10 frames)
│   GwtFrameSyncManager — main-thread-driven frame sync for GWT (no threads/locks)
│   InputBuffer, InputCommand (7 bools: up/down/left/right/punch/kick/special), InputCodec
│   FrameSyncManager  — lockstep + speculative execution + rollback (max 10 frames)
│   InputBuffer, InputCommand (7 bools: up/down/left/right/punch/kick/special), InputCodec
├── auth/               ★ new — JWT双Token认证
│   ├── AuthHttpServer      — HTTP 认证 API (端口 8081, com.sun.net.httpserver)
│   ├── AuthService         — 注册/登录/刷新/登出业务逻辑
│   ├── JwtTokenProvider    — JWT 生成 & 验证 (HMAC-SHA256, 15min access + 7d refresh)
│   ├── model/              — LoginRequest, RegisterRequest, TokenResponse, UserInfo
│   └── middleware/
│       AuthMiddleware      — Bearer token 鉴权拦截器
├── redis/              ★ new — Redis连接 + 降级内存模式
│   RedisService        — Jedis连接池, 用户CRUD, Token管理, 排行榜(ZSet), 在线状态
├── docker/             ★ new — 容器化
│   Dockerfile          — eclipse-temurin:17-jre-alpine
│   docker-compose.yml  — Redis 7 + Fyren 服务编排
├── match/
│   Matchmaker (ELO + diffusion window), MatchManager (preset forwarding, MMR update on result)
├── util/             InputCodec
├── tools/            SoundGenerator.java — procedural WAV generator (sin/noise/sweep)
├── GameMain.java     — CLI router: server/client/demo/register/login modes, --preset parsing, force IPv4 stack
├── GameServer.java   — UdpServer + WsGameServer + MatchManager + RedisService + AuthHttpServer + HttpStatusServer
└── GameClient.java   — UdpClient + FrameSyncManager + GameWorld + auth token methods
```

## libGDX Render Pipeline (current)

```
FyrenLauncher (main, CLI) → FyrenGame (ApplicationListener)
  → GameScreen
    ├── update(delta)
    │   ├── GdxInputHandler.samplePlayer1/2() → InputCommand
    │   ├── GameWorld.update(inputs, frameNumber)
    │   ├── HitEffects update + spawn on damage detected
    │   ├── ParticleEffects update + spawnHitSpark()
    │   ├── MotionTrailEffect.sample()
    │   ├── CameraController.update(p1, p2, delta)
    │   └── triggerAudio(p1, p2, dmg1, dmg2)  — consume fighter audio flags
    └── render()
        ├── BackgroundRenderer.render(cam)  — 4 layers: sky → mountains → bamboo → ground
        ├── SpriteRenderer (procedural textures + SpriteBatch)
        ├── MotionTrailEffect.render()
        ├── ParticleEffects.render()
        ├── HitEffects.render()
        └── HudRenderer.render()
```

## Key Design Decisions

- **Deterministic simulation** — GameWorld.update() sorts inputs by playerId. No floating-point RNG.
- **Rollback netcode** — GameStateSnapshot every 10 frames, rollback on prediction mismatch (max 10 frames).
- **Action state machine** — STARTUP → ACTIVE → RECOVERY → IDLE. STUN interrupts any phase. KAGE special cancels recovery.
- **Dash system** — ←←/→→ double-tap (200ms window), 3 charges, 3s recharge, dash attacks have halved startup and -5 damage.
- **Special resources** — KAGE: 3s CD. TAKESHI: accumulate 40 damage dealt. GOU: accumulate 50 damage taken.
- **Hitstun** — 5 + (dmg/5) frames, max 20. Throw on blocking target: +2 extra stun frames.
- **Clash** — both attacking same frame → each takes half damage + 3-frame short stun.
- **Preset negotiation** — client sends presetOrdinal in MatchRequestPacket, server relays to opponent via MatchResponsePacket.
- **Result reporting** — client sends ResultPacket on game end, server calls MatchManager.reportMatchResult() to update MMR.
- **Procedural textures** — SpriteRenderer generates limb/head textures at runtime (no sprite sheets needed).
- **Hit feedback pentology** — screen shake, hit-stop, hit sparks, damage flash, knockback displacement.
- **GWT/WebGL** — separate compile target (`FyrenGwt.gwt.xml`), demo mode + network mode via WebSocket.
- **WebSocket 跨平台** — server runs both UDP(9876) and WebSocket(9878), desktop+browser share match pool. GWT uses JSNI browser WebSocket, server uses org.java-websocket. Browser always server-relay (no P2P).
- **HTTP status API** — HttpStatusServer on port 8080, returns JSON with player count, Redis status, MMR rankings via /leaderboard.
- **Auth API** — AuthHttpServer on port 8081, JWT双Token机制 (access 15min + refresh 7d), bcrypt密码哈希, refresh token轮换防重放, Redis降级内存模式。
- **Redis 数据模型** — user:* (Hash), refresh:* (String TTL 7d), blacklist:* (TTL), online:* (TTL 30s), mmr:leaderboard (ZSet)
- **P2P UDP 打洞** — 匹配成功后异步发送 P2P_PING ×10（20ms 间隔），收到 P2P_PONG 即启用直连。2s 超时降级服务器中继。纯 Java 实现，无外部 STUN/TURN 依赖。
- **音效系统** — 6 个 CC0 WAV 文件（16-bit PCM mono 22050Hz），libGDX Sound API 加载播放。Fighter 使用 consume 模式标志（一次读取即清除）避免重复触发。GWT 后端静默降级。
- **World→Screen mapping** — screenX = screenCenter + (worldX - worldCenter) * scale, clamped to margins.

## Known Limitations

- P2P requires both clients behind NAT that supports UDP hole punching; symmetric NAT falls back to server relay.
- AudioManager silently degrades on GWT/WebGL (libGDX GWT backend has no Gdx.audio).
- Swing render files (`SwingGameWindow`, `DemoGameWindow`, `GamePanel`, `StickFigureRenderer`, `KeyInputHandler`) are superseded by libGDX layer but retained for backward compatibility.
- No background art (black background with procedural ground/grid lines).
- `float` coordinates — fine for single-platform (Java strictfp), not cross-platform deterministic.
- GWT/WebGL target compiles to JS (5 permutations, ~6.8MB each), served via `docs/fyren/index.html`. Requires manual `mvn dependency:sources` for gdx/gdx-backend-gwt before first build. Network mode (`?mode=network`) works via WebSocket — browser WSS via Caddy nip.io TLS proxy (`wss://<ip>.nip.io/ws` → localhost:9878).
- **GWT preloader assets path:** Preloader looks for assets at `docs/assets/` (parent of `docs/fyren/`). Must keep `docs/assets/assets.txt` + font files in sync with compiled JS expectations. Updated `gwt-compile.bat` to auto-copy assets to both locations.
- **GWT sound path:** Preloader looks for sounds at `assets/assets/sounds/` (double prefix) — GWT audio already silently degrades, so harmless.
- ECS Redis 未连接（内存模式，重启丢失用户数据）。
- WiX toolset 未安装 — jpackage 只能用 `--type app-image`（免安装目录），无法生成 EXE 安装包。分发方案: zip 压缩包 (~91MB, 含 JRE)。
- 无跳跃设计。

## ECS Deployment (2026-06-13 updated)

**Instance:** `i-bp10gn3btvuod4p2dpha`, cn-hangzhou
- **Public IP:** 115.29.230.57
- **OS:** Windows Server 2022 Datacenter (64-bit)

**Security Group** (`sg-bp10gn3btvuod4p9coge`):
| Port | Proto | Description |
|------|-------|-------------|
| 80 | TCP | IIS static site |
| 443 | TCP | Caddy HTTPS proxy (Let's Encrypt) |
| 3389 | TCP | RDP |
| 22 | TCP | SSH |
| 8080 | TCP | HTTP status API |
| 8081 | TCP | Auth API |
| 9876 | UDP | Game server |
| 9878 | TCP | **WebSocket (browser clients)** |
| ALL | ICMP | Ping |

**Running Services:**
| Service | Port | Status |
|---------|------|--------|
| Caddy HTTPS proxy | 443 | ✅ Let's Encrypt, `115.29.230.57.nip.io`, /ws → 9878 WSS |
| HTTP status API | 8080 | ✅ |
| Auth API | 8081 | ✅ |
| Game server (UDP) | 9876 | ✅ |
| Game server (WebSocket) | 9878 | ✅ v1.1 |
| Watchdog tasks | — | ✅ FyrenServer + FyrenCaddy (every 5min, SYSTEM)

**Deployment:** `scripts/ecs-deploy.ps1` — auto backup + replace JAR + update Caddyfile + restart
- `C:\Fyren\Fyren-1.0-SNAPSHOT.jar` — fat JAR
- `C:\Fyren\caddy\Caddyfile` — HTTPS proxy + CORS headers
- `C:\Fyren\logs\` — server + caddy stdout logs
- Daemon mode (`--daemon`) + `schtasks` watchdog (auto-restart on crash/reboot)

## Data Flow

```
libGDX mode (current production):
  Demo:
    Keyboard → GdxInputHandler (Gdx.input polling)
      → InputCommand
      → GameScreen.updateDemo()
      → GameWorld.update() → CollisionSystem

  Network (FrameSyncManager with P2P):
    Keyboard → GdxInputHandler → InputCommand
      → GameScreen.updateNetwork() → GameClient.setCurrentLocalInput() + sendInputToOpponent()
      → UdpClient.sendInputToOpponent() — P2P 直连 if active, else 服务器中继
    FrameSyncManager.gameLoop (independent thread) → GameWorld.update() → CollisionSystem
    libGDX render thread → GameScreen.render() → reads GameWorld via GameClient.getGameWorldReadLocked()

  GameScreen.render()
    → SpriteRenderer (procedural textures + SpriteBatch)
    → HitEffects / ParticleEffects / MotionTrailEffect
    → HudRenderer

WebGL Demo (GWT compiled JS):
  Keyboard → GdxInputHandler (Gdx.input polling) → InputCommand
    → FyrenGwtLauncher (inline game loop) → GameWorld.update() → CollisionSystem
    → SpriteRenderer / HitEffects / ParticleEffects / MotionTrailEffect / HudRenderer

WebGL Network (GWT + WebSocket):
  Keyboard → GdxInputHandler → InputCommand
    → FyrenGwtLauncher.renderNetwork() → GwtNetworkClient.submitInput()
    → GwtWebSocket.send() → server(WS:9878) → opponent
    GwtFrameSyncManager.tick(delta) → GameWorld.update() → CollisionSystem
    → SpriteRenderer / HitEffects / ParticleEffects / MotionTrailEffect / HudRenderer

Swing mode (legacy, retained):
  Keyboard → KeyInputHandler → InputCommand
  Swing Timer → GamePanel.repaint() → StickFigureRenderer
```

## Current Session (2026-06-22) — P2 全部完成 + 攻击框可视化

### P2 核心交付

- **JWT 匹配鉴权** — `MatchRequestPacket` 携带 JWT，`GameServer.verifyMatchAuth()` 验证签名/过期/sub/type。UDP 客户端必须验证，WebSocket Demo 豁免（Guest 模式）
- **HTTP 热部署** — `curl -X POST --data-binary @JAR http://IP:8081/admin/deploy`，无需 RDP
- **训练模式角色切换** — `1/2/3` 切换 P1，`Shift+1/2/3` 切换假人
- **攻击框可视化** — 绿色受击框 + 红色攻击框（判定帧），ShapeRenderer 绘制

### 测试覆盖：10 → 33 条 (5 文件)

| 新增 | 文件 | 覆盖 |
|------|------|------|
| +6 | `MatchRequestPacketTest` | JWT 序列化/向后兼容/边界 |
| +7 | `CollisionSystemTest` | 命中/格挡/投技/相杀/分离 |
| +10 | `FighterActionTest` | 状态机/STUN/资源/防御 |

### Commit 记录
```
ddd6694 feat: training mode hitbox visualization
13f8d75 fix: P2 #1-#4 — GWT guest mode + Redis/JWT env vars + 23 new tests
d619365 docs: add README.md
c1bd190 feat: P2 — JWT match auth + training char select + deploy fix
```

### 历史 (2026-06-21) — P1 Bug #27 + #28 修复
### 历史 (2026-06-20) — P1 全部完成：姿态动画 + 视差背景 + 训练模式

### 2. 姿态动画系统 (ba64c24)
**问题:** 所有动作共用同一套 stick figure 姿势，缺乏反馈感。
**修复:** `SpriteRenderer.java` — 每个 `FighterStance` 独立姿态：
- 腿：KICK 高踢/DASH 后蹬/WALK 交替/BLOCK 并拢/HURT 曲腿/SPECIAL 马步
- 躯干：PUNCH 前倾/KICK 后仰/DASH 大倾角/HURT 后仰/SPECIAL 沉腰
- 手臂：PUNCH 直拳/KICK 展开平衡/THROW 前伸/SPECIAL 高举/BLOCK 交叉格挡/HURT 下垂/DASH 后摆
- 头部：随动作偏移（PUNCH 前探/KICK 后缩/DASH 大幅度前移）
- IDLE 呼吸起伏 (sin 1.5px)，WALK 摆臂 + 双腿交替

### 3. 多层视差背景 (d493892)
**问题:** 背景只有纯黑 + 灰色地面方块，缺乏场景感。
**新增:** `BackgroundRenderer.java` — 4 层全程程序化生成，无外部素材：
- 天空：渐变色 + 月亮 + 80 颗闪烁星点
- 远山：双层剪影（0.15x 视差），正弦叠加 + 随机扰动
- 中景：60 棵竹/松树（0.4x 视差），竹有节+叶，松有锥形叠层
- 地面：土色 + 草地线 + 石块纹理 + 中线分隔
- 替换 `GameScreen` 和 `FyrenGwtLauncher` 中的旧 `drawBackground()`

### 4. 训练模式 (badcae3)
**问题:** TitleScreen「训练模式」显示 COMING SOON，无实际功能。
**新增:** `TrainingScreen.java` — 独立训练画面：
- 单人自由练习（P1 操作，P2 假人站立不动，击倒后自动回血）
- 帧数据显示：当前姿态、动作类型、启动/判定/收尾帧进度条
- 输入状态实时显示（↑↓←→ 拳踢特防）
- 特殊资源追踪（影 CD / 武伤害积累 / 刚受伤积累）+ 冲刺次数
- ESC 返回标题画面
- `FyrenGame` 新增 `ScreenState.TRAINING` + `enterTrainingMode()`

### 5. ECS 服务器状态验证
- 8081 (Auth API): ✅ 注册/登录/JWT 正常
- 9878 (WebSocket): ✅ 运行中
- 9876 (UDP): ✅ 同进程
- 8080 (Status): ❌ Connection refused（HttpStatusServer 挂了，不影响对战）

### 会话语录
```
badcae3 feat: training mode — solo practice with frame data overlay, input display, dummy opponent
d493892 feat: multi-layer parallax background renderer — procedural sky/mountains/bamboo/ground
ba64c24 feat: stance-dependent stick figure animation — all 10 stances with unique poses
7899ec8 docs: update CLAUDE.md + memory — EXE networking session 2026-06-20
7099d39 feat: LoginScreen replaces NetworkSetupScreen — login once, server IP hidden, Chinese menus
a6137a4 feat: EXE network setup + CJK font rendering
```

## Historical Session (2026-06-19) — WSS 联网修复 + EXE 分发 + ECS 恢复

## Historical Session (2026-06-19) — WSS 联网修复 + EXE 分发 + ECS 恢复

### ECS 宕机诊断 & 恢复

**根因:** `HttpStatusServer` 所有 handler 未调 `exchange.close()`，导致 CLOSE_WAIT 堆积（50+ 连接）。TCP backlog 被占满后 8080 端口拒绝所有新连接。Java 进程最终崩溃。

**修复:**
- `HttpStatusServer.java` — StatusHandler / LeaderboardHandler / /health lambda 全部包裹 `try { ... } finally { exchange.close(); }`
- ECS 上 Java 进程已崩溃（`Get-Process java` 找不到），手动重启恢复

### 联网对战 WSS 修复

**问题:** GitHub Pages 强制 HTTPS，GWT 客户端用 `ws://` 直连被浏览器 Mixed Content 阻断。

**修复 (3 处):**
| 文件 | 改动 |
|------|------|
| `GwtNetworkClient.java` | `Window.Location.getProtocol()` 检测 HTTPS → 自动用 `wss://<ip>.nip.io/ws` |
| `scripts/Caddyfile` | 新增 `handle /ws* { reverse_proxy 127.0.0.1:9878 }` |
| ECS Caddyfile | 重写（原文件被意外清空），reload Caddy |

**WSS 链路:** `Browser(HTTPS) → WSS → Caddy:443/ws → localhost:9878(WsGameServer)`

**验证:** 隔离浏览器上下文实测，2 条 WSS 连接 ESTABLISHED。

### Windows EXE 分发

**构建:** `jpackage --type app-image`（WiX 不可用，无法生成安装包）→ zip 分发
- 输入: `target/pkg-input/Fyren-1.0-SNAPSHOT.jar`
- 输出: `target/pkg-out/Fyren/` (215MB, 含 GraalVM JRE)
- Zip: `Fyren-windows-x64.zip` (91MB)

**发布:** `gh release upload v1.2` → GitHub Release Assets

**网页:** `docs/index.html` 下载按钮改为 "下载 Windows 版 (EXE · 91MB)"，点确认后直链 zip

### GWT 重编译

- 5 permutations 编译通过 (~90s)
- 旧 cache.js 文件清理（`docs/fyren/` 下 5 个 Jun-13 文件 → 5 个 Jun-19 文件）
- `docs/assets/` + `docs/fyren/` 同步更新

### 4 项检查最终状态

| # | 检查项 | 状态 |
|---|--------|------|
| 1 | WebGL Demo | ✅ 正常 |
| 2 | 联网对战 (?mode=network) | ✅ WSS 已修复 |
| 3 | GitHub Release v1.2 下载 | ✅ JAR + EXE zip |
| 4 | ECS 服务器状态面板 | ✅ 已恢复 |

### 会话语录
```
4f0feff fix: WSS proxy for browser network mode + HttpStatusServer connection leak
9d4515b feat: switch download from JAR to EXE (jpackage app-image, 91MB zip)
```
## Historical Session (2026-06-14) — Bug #25 + #26 修复

### Bug #25 (P0): GWT preloader 资源路径错误 ✅

**根因:** libGDX GWT preloader 运行时从 `parentOf(moduleUrl) + 'assets/'` 加载 `assets.txt`。部署目录 `docs/fyren/` 对应的路径是 `docs/assets/`，但该目录不存在，导致 preloader 404 → 字体未加载 → `BitmapFont(null)` → NPE 崩溃。

**修复:**
- 新建 `docs/assets/` — `assets.txt` + `lsans-15.fnt` + `lsans-15.png` + `version.txt`
- 更新 `gwt-compile.bat` — 编译后复制资源到 `target/gwt-out/assets/`（除 `target/gwt-out/fyren/` 之外）

**验证:** 线上 `https://uquc.github.io/Fyren/fyren/` — 全部资源 200，Canvas 960x540 正常渲染。

### Bug #26 (P2): hit-stop 双重 update ✅

**根因:** `hitEffects.update(delta)` 在 hit-stop `if` 分支内调一次，后面无条件又调一次，导致 hit-stop 倒计时速度翻倍、打击感减弱。

**修复 (3 处):**
- `FyrenGwtLauncher.java` `renderDemo()` — 移除 if 内 `hitEffects.update(delta)`
- `FyrenGwtLauncher.java` `renderNetwork()` — 同上
- `GameScreen.java` — 同上（桌面版也有此问题）

### 次要发现

- **音效路径双重前缀:** preloader 查找 `assets/assets/sounds/*.wav`（应为 `assets/sounds/*.wav`）— 音效文件在 GWT 原本就静默降级，暂不影响游戏。
- `assets/sounds/` 目录已存在于 `D:/develp/Fyren/assets/sounds/`，由 libGDX SoundGenerator 生成。

**Commit:** `8eb70d5` — 已推送到 GitHub + Gitee

## Historical Session (2026-06-13) — GWT WebSocket 网络对战完成 + ECS 更新

### GWT WebSocket — P1-4 完成 ✅

**11 个 Task 全部完成** (plan: `docs/superpowers/plans/2026-06-13-gwt-websocket.md`)

| Task | 内容 | Commit |
|------|------|--------|
| 1 | pom.xml: org.java-websocket 1.5.6 | — |
| 2 | WsSession.java — WebSocket conn state | — |
| 3 | WsGameServer.java — WebSocket server (9878) | — |
| 4 | MatchManager: transport-agnostic callbacks | — |
| 5 | GameServer: integrate WsGameServer | `c7817ea` |
| 6 | GwtWebSocket.java — JSNI browser WebSocket | — |
| 7 | GwtFrameSyncManager.java — main-thread frame sync | `19c072f` |
| 8 | GwtNetworkClient.java — GWT network client | `b61f546` |
| 9 | FyrenGwt.gwt.xml: add source paths | `a4540bb` |
| 10 | FyrenGwtLauncher: ?mode=network support | `15a7311` |
| 11 | GWT compile (91s, 5 perms) + verify | ✅ |

**New files (5):**
- `network/WsGameServer.java` — WebSocket server, binary frames, shared match pool
- `network/WsSession.java` — WebSocket conn state
- `network/gwt/GwtWebSocket.java` — JSNI browser WebSocket (ArrayBuffer binary)
- `network/gwt/GwtNetworkClient.java` — same state machine as GameClient, WS transport
- `sync/GwtFrameSyncManager.java` — main-thread-driven, no threads/locks

**Modified files (5):**
- `GameServer.java` — starts WsGameServer on 9878, transport-agnostic MatchManager
- `MatchManager.java` — MatchResponseSender interface + onMatchFoundCallback
- `FyrenGwt.gwt.xml` — adds network/gwt, network, sync, match, util source paths
- `FyrenGwtLauncher.java` — demo/network mode dispatch via URL params
- `pom.xml` — org.java-websocket:Java-WebSocket:1.5.6

### GitHub Pages 修复

- `docs/fyren/assets.txt` — 从空文件修复为正确资源清单（字体崩溃根因）
- `gwt-compile.bat` — 资源输出路径从 `assets/` 改为 `fyren/`（GWT 预加载器在页面同级查找）
- 产品页 `docs/index.html` → 新增跨平台联机 + P2P 卡片

### ECS 部署 (2026-06-13)

- 新版 JAR (v1.1, 18MB) 含 WsGameServer → `C:\Fyren\`
- Caddyfile 更新（CORS headers）
- `scripts/ecs-deploy.ps1` — 自动备份+替换+重启
- 安全组 TCP 9878 已开放（MCP 浏览器操作）
- Watchdog: `schtasks` FyrenServer + FyrenCaddy (每5分钟, SYSTEM)

### 网页体验版

- **本地 Demo:** `https://uquc.github.io/Fyren/fyren/` (P1 WASD+JKU, P2 方向键+123)
- **联网对战:** `https://uquc.github.io/Fyren/fyren/?mode=network&server=115.29.230.57&playerId=<ID>`
- 浏览器始终走服务器中继（无 P2P），桌面客户端仍可使用 UDP 打洞

### 会话语录
```
bdce6fd ops: add ecs-deploy.ps1 — automated JAR + Caddyfile deploy with backup
2cfea4c fix: GWT assets placement — put assets.txt and fonts in module directory
90cb7a2 fix: rewrite ECS setup script — schtasks + watchdog, zero deps
```

## Historical Session (2026-06-10) — P0 完成

**P0 全部完成** (commit: `575c16d` feat: P0 — P2P UDP hole punch + audio system, 18 files, +447/-22)

### P0-1: 音效系统
- `AudioManager.java` — 加载 6 个 WAV 文件（hit_light/hit_heavy/special/dash/block/ko）通过 `Gdx.audio.newSound()`
- `Fighter.java` — `consumeAudioDashTrigger()` / `consumeAudioSpecialTrigger()` / `consumeAudioBlockedTrigger()` consume 模式标志
- `CollisionSystem.java` — 格挡时设置 `audioBlockedTrigger`（在 `takeDamage()` 清除 `isBlocking` 之前）
- `GameScreen.java` — `triggerAudio(p1, p2, dmg1, dmg2)`：命中/冲刺/特殊技/格挡/KO 五种音效触发
- `FyrenGwtLauncher.java` — 同上，GWT 静默降级
- `tools/SoundGenerator.java` — 程序化 WAV 生成器（sin 波/噪音/扫频），生成 6 个音效文件到 `assets/sounds/`

### P0-2: P2P UDP 打洞
- `P2PHandshake.java` (NEW) — 异步 NAT 穿透：发送 P2P_PING ×10（20ms 间隔），等待 P2P_PONG，2s 超时→降级中继
- `P2pPacket.java` (NEW) — 最小打洞包（仅 8 字节包头，无 payload）
- `Packet.java` — 新增 `P2P_PING(8)` / `P2P_PONG(9)` 类型
- `UdpClient.java` — `sendInputToOpponent()` P2P 路由（`p2pActive ? p2pAddress : serverAddress`），`sendRaw()` 握手机制
- `GameClient.java` — 匹配成功后启动 P2PHandshake，处理 P2P_PING/P2P_PONG

### 前置修复 (2026-06-10)
- `GameServer.java` — activeMatches/totalMatches 计数器修复 + ResultPacket 去重
- `FrameSyncManager.java` — 游戏结束检测 + `onGameOver` 回调
- `GameClient.java` — game-over 回调：world winnerId → player IDs → reportResult()
- `HttpStatusServer.java` — incrementActiveMatches()/decrementActiveMatches()

### 集成测试验证
| 功能 | 结果 |
|------|------|
| 音效加载 (6 WAV) | ✅ |
| 音效触发 (hit/dash/special/block/KO) | ✅ |
| P2P 握手 (localhost) | ✅ 双方 "打洞成功! 直连" |
| 计数器 (online/active/total matches) | ✅ 0→2→0, 0→1→0, 0→1 |
| GWT 编译 | ✅ 87.6s, 5 permutations |
| 所有单元测试 | ✅ BUILD SUCCESS |

### 会话语录
```
575c16d feat: P0 — P2P UDP hole punch + audio system
de17cf1 docs: P2P UDP hole punch + audio system design spec
7ede323 fix: activeMatches/totalMatches counters + game-over detection + ResultPacket reporting
```

## P1 全部完成 ✅ (更新于 2026-06-20)

1. ~~**主菜单/UI**~~ ✅ — 6 个 Screen + FyrenGame 状态机 + fade 转场
2. ~~**背景/视觉美术资源**~~ ✅ — 4 层程序化视差背景（天空/远山/竹林/地面）
3. ~~**训练模式**~~ ✅ — 帧数据显示 + 输入状态 + 假人对战
4. ~~**GWT WebSocket 网络对战**~~ ✅ (2026-06-13)

## P2 全部完成 ✅ (更新于 2026-06-22)

| # | 项目 | 状态 | Commit |
|---|------|------|--------|
| 1 | ECS 部署新版 JAR | ✅ HTTP `/admin/deploy` 热部署 | `c1bd190` |
| 2 | HttpStatusServer 8080 修复 | ✅ 重启恢复 | `c1bd190` |
| 3 | JWT Token 匹配鉴权 | ✅ UDP 必须验证/WS Guest 豁免 | `c1bd190` |
| 4 | 训练模式角色选择 | ✅ `1/2/3` 键切换 | `c1bd190` |
| 5 | 背景图美术升级 | ❌ 跳过（素材尺寸不足）| — |
| + | GWT 网页联网修复 | ✅ WS Guest 模式 | `13f8d75` |
| + | Redis/JWT_SECRET env vars | ✅ restart.bat 已设定 | `13f8d75` |
| + | 测试覆盖 10→33 条 | ✅ 3 新测试文件 | `13f8d75` |
| + | 训练模式攻击框可视化 | ✅ ShapeRenderer 绿/红框 | `ddd6694` |
| + | README.md | ✅ 项目概述/架构/键位 | `d619365` |

## Historical Session (2026-06-09) — Bug 修复 + ECS E2E

全部 Bug (#14-#23) 修复验证 + ECS 远程端到端测试通过 + Windows IPv4 双栈修复。
- `GameMain.main()` 强制 `-Djava.net.preferIPv4Stack=true`（ECS UDP 通信失败的根因）
- `/admin/deploy` 热更新端点就绪
- ECS 运维：RDP 传 deploy.zip → `netsh advfirewall` 开 UDP 9876

Last commits: `93cd50e` IPv4 修复, `ab8e008` 会话文档化

## Historical Session (2026-06-07) — GWT/WebGL 编译管线

GWT 编译成功 + 游戏核心 GWT 兼容化（Rect 替代 Rectangle, int RGBA 替代 Color, String.format 替代）。
ECS 部署包 `target/deploy.zip` (57MB, jre-minimal + fat JAR)。

Last commit: `ae70368 fix: NPE in Packet.deserialize crashes UDP receive thread`
