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

# jpackage EXE
mvn package -q
jpackage --input target --name Fyren --main-jar Fyren-1.0-SNAPSHOT.jar --main-class com.Fyren.render.libgdx.FyrenLauncher --type exe --win-console --java-options "-XstartOnFirstThread"
```

Dependencies: Lombok (provided), JUnit Jupiter 5.10.2 (test), libGDX 1.12.1 (gdx, gdx-backend-lwjgl3, gdx-platform:natives-desktop), jjwt 0.12.5 (api/impl/jackson), Jedis 5.1.2, jBCrypt 0.4.

## Architecture

**Fyren** is a 2D fighting game with UDP networking, hidden-MMR matchmaking, lockstep + rollback frame sync, and libGDX rendering. Java 17+, Maven, single jar for both client and server. ~52 source files.

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
│   │   AudioManager      — sound effect skeleton (Gdx.audio, currently silent)
│   │   └── gwt/
│   │       FyrenGwtLauncher — GWT/WebGL entry point (demo mode only, no network)
│   ├── SwingGameWindow   — JFrame 960×540, Swing Timer 16ms, for network client mode
│   ├── DemoGameWindow    — standalone dual-keyboard demo (no GameClient dependency)
│   ├── GamePanel         — JPanel paintComponent, world→screen coordinate mapping
│   ├── StickFigureRenderer — static Java2D stick-figure drawing per stance/preset
│   └── KeyInputHandler   — KeyListener with key-state bitset, double-tap dash detection, P1/P2 mappings
├── network/
│   Packet (abstract)     — 8-byte header, Type enum: INPUT/STATE/HEARTBEAT/MATCH_REQ/MATCH_RES/ACK/RESULT
│   MatchRequestPacket    — playerId, rating, presetOrdinal
│   MatchResponsePacket   — opponentId, rating, address, opponentPresetOrdinal
│   ResultPacket          — player1Id, player2Id, winnerId (game result → MMR update)
│   HttpStatusServer      — embedded HTTP server on port 8080, /status JSON endpoint
│   UdpClient, UdpServer  — reliable(ACK+retransmit) + unreliable(fire-and-forget) channels
├── sync/
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
├── GameMain.java     — CLI router: server/client/demo/register/login modes, --preset parsing
├── GameServer.java   — UdpServer + MatchManager + RedisService + AuthHttpServer + HttpStatusServer
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
    │   └── CameraController.update(p1, p2, delta)
    └── render()
        ├── drawBackground (ground + grid via ShapeRenderer)
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
- **GWT/WebGL** — separate compile target (`FyrenGwt.gwt.xml`), demo-only (no java.net.* in browser).
- **HTTP status API** — HttpStatusServer on port 8080, returns JSON with player count, Redis status, MMR rankings via /leaderboard.
- **Auth API** — AuthHttpServer on port 8081, JWT双Token机制 (access 15min + refresh 7d), bcrypt密码哈希, refresh token轮换防重放, Redis降级内存模式。
- **Redis 数据模型** — user:* (Hash), refresh:* (String TTL 7d), blacklist:* (TTL), online:* (TTL 30s), mmr:leaderboard (ZSet)
- **World→Screen mapping** — screenX = screenCenter + (worldX - worldCenter) * scale, clamped to margins.

## Known Limitations

- No P2P yet — all input through server relay.
- Swing render files (`SwingGameWindow`, `DemoGameWindow`, `GamePanel`, `StickFigureRenderer`, `KeyInputHandler`) are superseded by libGDX layer but retained for backward compatibility.
- No sound effects loaded (AudioManager is skeleton only).
- No background art (black background with procedural ground/grid lines).
- `float` coordinates — fine for single-platform (Java strictfp), not cross-platform deterministic.
- GWT/WebGL target compiles to JS (5 permutations, ~6.8MB each), served via `target/gwt-out/index.html`. Requires manual `mvn dependency:sources` for gdx/gdx-backend-gwt before first build.

## ECS Deployment (Task 11 — DONE 🎉)

**Instance:** `i-bp10gn3btvuod4p2dpha`, cn-hangzhou
- **Public IP:** 115.29.230.57
- **Private IP:** 172.16.220.228
- **OS:** Windows Server 2022 Datacenter (64-bit)
- **Password:** Fyren@2026!Server (Administrator)

**Security Group** (`sg-bp10gn3btvuod4p9coge`):
- TCP 80 (HTTP — portfolio website)
- TCP 8080 (HTTP status API)
- TCP 8081 (Auth API) — ⚠️ 需手动添加，不在默认规则中
- UDP 9876 (game server)
- TCP 22 (SSH)
- ALL ICMP
- TCP 3389 (RDP)

**Running Services:**
| Service | Port | Status |
|---------|------|--------|
| Portfolio website | 80 | ✅ |
| HTTP status API | 8080 | ✅ |
| Auth API | 8081 | ✅ (local only, not yet on ECS) |
| Game server (UDP) | 9876 | ✅ |

**Deployment method:** RDP file transfer + jlink minimal JRE (java.base, java.logging, java.net.http, java.management, jdk.unsupported, jdk.httpserver)
- `C:\Fyren\` — fat JAR + JRE
- `C:\inetpub\wwwroot\index.html` — IIS static site
- Daemon mode (`--daemon` flag) + shutdown hook for graceful stop
- Firewall rules configured via netsh

## Data Flow

```
libGDX mode (current production):
  Demo:
    Keyboard → GdxInputHandler (Gdx.input polling)
      → InputCommand
      → GameScreen.updateDemo()
      → GameWorld.update() → CollisionSystem

  Network (new — FrameSyncManager integrated):
    Keyboard → GdxInputHandler → InputCommand
      → GameScreen.updateNetwork() → GameClient.setCurrentLocalInput() + sendInputToOpponent()
    FrameSyncManager.gameLoop (independent thread) → GameWorld.update() → CollisionSystem
    libGDX render thread → GameScreen.render() → reads GameWorld via GameClient.getGameWorldReadLocked()

  GameScreen.render()
    → SpriteRenderer (procedural textures + SpriteBatch)
    → HitEffects / ParticleEffects / MotionTrailEffect
    → HudRenderer

Network mode (planned — FrameSyncManager integration pending):
  Keyboard → GdxInputHandler → InputCommand
    → FrameSyncManager.collectLocalInput()
    → UdpClient.sendUnreliable()
  FrameSyncManager.gameLoop → GameWorld.update() → CollisionSystem
  libGDX render thread → GameScreen.render()

WebGL (GWT compiled JS):
  Keyboard → GdxInputHandler (Gdx.input polling) → InputCommand
    → FyrenGwtLauncher (inline game loop) → GameWorld.update() → CollisionSystem
    → SpriteRenderer / HitEffects / ParticleEffects / MotionTrailEffect / HudRenderer

Swing mode (legacy, retained):
  Keyboard → KeyInputHandler → InputCommand
  Swing Timer → GamePanel.repaint() → StickFigureRenderer
```

## Current Session (2026-06-09)

**本次完成:** 全部 Bug 修复 + ECS 远程端到端测试通过 + Windows IPv4 双栈修复。

### 变更摘要
1. **Bug #20–#23 全部修复验证:**
   - #20 P2 输入映射 — 验证 swap 已实现，DirectionTest 8/8 通过
   - #21 DirectionTest 编译错误 — `Rect`→`java.awt.Rectangle` 适配（已在 working tree）
   - #22 GWT preloader 卡 0% — `gwt-assets/version.txt` + `FyrenGwtLauncher.getPreloaderCallback()` override + `gwt-compile.bat` 后处理
   - #23 runLogin() MATCHED 竞态 — 已在 commit `1b6c321` 修复
2. **`GameMain.main()` 强制 IPv4 栈** — `System.setProperty("java.net.preferIPv4Stack", "true")`。修复 Java 在 Windows Server 双栈环境下 `DatagramSocket(port)` 绑定 IPv6-mapped 地址导致外网 IPv4 客户端 UDP 包无法到达的问题。**这是 ECS 远程通信失败的根因。**

### ECS 远程 E2E 测试 ✅
- ✅ UDP 通信正常（IPv4 修复后 onlinePlayers 实时更新 0→1→2）
- ✅ MatchResponsePacket 序列化正确（无 BufferOverflowException）
- ✅ 双客户端匹配流程：CONNECT → MATCHED → PLAYING
- ✅ 竞态条件修复生效（MATCHED→PLAYING 无提前断开）
- ✅ `/admin/deploy` 端点就绪（新 JAR 已含 DeployHandler，后续可热更新）

### ECS 运维笔记
- **启动命令（必须含 IPv4 参数）:**
  ```cmd
  C:\Fyren\jre-minimal\bin\java -Djava.net.preferIPv4Stack=true -cp C:\Fyren\Fyren-1.0-SNAPSHOT.jar com.Fyren.GameMain server 9876 --daemon
  ```
- 首次部署需 RDP 传 `deploy.zip`，后续可用 `POST /admin/deploy`
- 防火墙：`netsh advfirewall firewall add rule name="Fyren Game UDP 9876" dir=in action=allow protocol=UDP localport=9876`

### 已知限制
- `activeMatches`/`totalMatches` 计数器未更新（待调查，不影响核心流程）
- GWT 需重新编译生成 assets.txt
- ECS Redis 未连接（内存模式，重启丢失用户数据）

### 下次会话
1. activeMatches/totalMatches 计数器修复
2. GWT 重新编译验证
3. 压力测试 / 稳定性测试

Last commit: `93cd50e fix: force IPv4 stack to fix Windows dual-stack UDP receive issue`

## Historical Session (2026-06-07)

**本次完成:** GWT/WebGL 编译管线 + 游戏核心 GWT 兼容化 + ECS 部署包准备。

### 变更摘要 (Phase 1: libGDX 网络集成)
1. **FrameSyncManager Bug 修复** — `predictInputs()` 处理本地玩家输入缺失，新增 `copyInput()`
2. **GameScreen 网络模式** — `updateNetwork()` → GameClient → FrameSyncManager，ReadWriteLock 线程安全
3. **FyrenGame/FyrenLauncher/GameMain** — 网络客户端模式集成

### 变更摘要 (Phase 2: GWT/WebGL 编译管线 ✅)
1. **GWT 编译成功** → 输出 `target/gwt-out/fyren/*.js` (5 排列 ~6.8MB each)
2. **FyrenGwt.gwt.xml** — 模块文件移到 `com.Fyren/` 级别，canonical source paths
3. **FyrenGwtLauncher** — 自包含 WebGL Demo（不依赖 FyrenGame/GameScreen/GameClient）
4. **GWT 兼容化改造:**
   - 创建 `com.Fyren.game.Rect` — 替代 `java.awt.Rectangle`
   - `FighterPreset.lineColor` — `java.awt.Color` → `int` (packed RGBA)
   - `Fighter.java` — `getHitbox()/getAttackBox()` 返回 `Rect` 而非 `Rectangle`
   - `CollisionSystem.java` — 使用 `Rect` 替代 `Rectangle`
   - `HudRenderer.java` — `String.format()` → 手动 `pad2()` (GWT 不支持)
   - `StickFigureRenderer.java` — 添加 `toColor(int)` 转换方法
   - `GamePanel.java` — Rect → java.awt.Rectangle 适配
5. **Chrome.gwt.xml 占位** — 创建于 `src/main/java/com/badlogic/gdx/backends/gwt/theme/chrome/`（后删除，因 gdx-backend-gwt-sources.jar 已下载）
6. **gwt-compile.bat/sh** — 完整 classpath（gwt-dev + transitive deps: asm, colt, gson, tapestry, ant, jsr305, validation）
7. **CLI 命令:**
   ```bash
   mvn compile -q
   mvn dependency:sources -DincludeArtifactIds=gdx-backend-gwt,gdx  # 首次
   ./gwt-compile.sh   # or gwt-compile.bat
   ```
8. **GWT classpath 依赖链:** gwt-dev, gwt-user, gdx + sources, gdx-backend-gwt + sources, jsinterop-annotations + sources, validation-api + sources, ant + launcher, colt, asm + util + commons, gson, jsr305, tapestry

### ECS 部署状态
- `target/deploy.zip` (57MB, jre-minimal + fat JAR) ✅
- `deploy-ecs.ps1` ✅  

Last commit: `ae70368 fix: NPE in Packet.deserialize crashes UDP receive thread + add catch-all exception handler`
