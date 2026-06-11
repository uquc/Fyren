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
│   │   └── gwt/
│   │       FyrenGwtLauncher — GWT/WebGL entry point (demo mode only, no network)
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
│   HttpStatusServer      — embedded HTTP server on port 8080, /status JSON endpoint, /admin/deploy hot-update
│   UdpClient, UdpServer  — reliable(ACK+retransmit) + unreliable(fire-and-forget) channels, P2P routing
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
├── tools/            SoundGenerator.java — procedural WAV generator (sin/noise/sweep)
├── GameMain.java     — CLI router: server/client/demo/register/login modes, --preset parsing, force IPv4 stack
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
    │   ├── CameraController.update(p1, p2, delta)
    │   └── triggerAudio(p1, p2, dmg1, dmg2)  — consume fighter audio flags
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
- **P2P UDP 打洞** — 匹配成功后异步发送 P2P_PING ×10（20ms 间隔），收到 P2P_PONG 即启用直连。2s 超时降级服务器中继。纯 Java 实现，无外部 STUN/TURN 依赖。
- **音效系统** — 6 个 CC0 WAV 文件（16-bit PCM mono 22050Hz），libGDX Sound API 加载播放。Fighter 使用 consume 模式标志（一次读取即清除）避免重复触发。GWT 后端静默降级。
- **World→Screen mapping** — screenX = screenCenter + (worldX - worldCenter) * scale, clamped to margins.

## Known Limitations

- P2P requires both clients behind NAT that supports UDP hole punching; symmetric NAT falls back to server relay.
- AudioManager silently degrades on GWT/WebGL (libGDX GWT backend has no Gdx.audio).
- Swing render files (`SwingGameWindow`, `DemoGameWindow`, `GamePanel`, `StickFigureRenderer`, `KeyInputHandler`) are superseded by libGDX layer but retained for backward compatibility.
- No background art (black background with procedural ground/grid lines).
- `float` coordinates — fine for single-platform (Java strictfp), not cross-platform deterministic.
- GWT/WebGL target compiles to JS (5 permutations, ~6.8MB each), served via `target/gwt-out/index.html`. Requires manual `mvn dependency:sources` for gdx/gdx-backend-gwt before first build.
- ECS Redis 未连接（内存模式，重启丢失用户数据）。
- 无跳跃设计。

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
| Portfolio website (IIS) | 80 | ✅ |
| Caddy HTTPS proxy | 443 | ✅ Let's Encrypt, `115.29.230.57.nip.io` |
| HTTP status API | 8080 | ✅ |
| Auth API | 8081 | ✅ |
| Game server (UDP) | 9876 | ✅ |

**Deployment method:** RDP file transfer + jlink minimal JRE (java.base, java.logging, java.net.http, java.management, jdk.unsupported, jdk.httpserver)
- `C:\Fyren\` — fat JAR + JRE
- `C:\Fyren\caddy\` — Caddy v2.7.6 + Caddyfile（HTTPS 反向代理）
- `C:\inetpub\wwwroot\index.html` — IIS static site
- Daemon mode (`--daemon` flag) + shutdown hook for graceful stop
- Firewall rules configured via netsh
- Caddy HTTPS via `Start-ScheduledTask -TaskName "Fyren-Caddy-HTTPS"` (auto-start on boot)

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

WebGL (GWT compiled JS):
  Keyboard → GdxInputHandler (Gdx.input polling) → InputCommand
    → FyrenGwtLauncher (inline game loop) → GameWorld.update() → CollisionSystem
    → SpriteRenderer / HitEffects / ParticleEffects / MotionTrailEffect / HudRenderer

Swing mode (legacy, retained):
  Keyboard → KeyInputHandler → InputCommand
  Swing Timer → GamePanel.repaint() → StickFigureRenderer
```

## Current Session (2026-06-11) — DevOps: GitHub Pages + ECS HTTPS

**无代码变更** — 本次会话做的是部署/运维。

### GitHub Pages 网站
- `docs/index.html` — 产品页（中文，暗色主题，状态面板 + WebGL iframe + 合规下载按钮）
- `docs/fyren/` — GWT WebGL 编译产物（59MB，5 browser permutations）
- `docs/.nojekyll` — 防止 GitHub Pages 把文件当 Jekyll 处理
- Commit `71d11be` — PAN_URL 改为 `/releases/latest`
- ⚠️ **GitHub Pages 尚未启用** — 需去 Settings → Pages → master /docs → Save

### GitHub Release v0.3.0 修复
- Release 描述：从空白修复为完整中文 changelog（P1-1 + P0 功能清单）
- 标签：Pre-release → Latest
- 下载链接：`/releases` → `/releases/latest`
- ZIP 资产：重新上传为 Release Asset（不再 user-attachment 404）
- Registry: `https://github.com/uquc/Fyren/releases/tag/v0.3.0`

### ECS HTTPS (Caddy + Let's Encrypt)
- Caddy v2.7.6 反向代理，`115.29.230.57.nip.io`
- Let's Encrypt TLS-ALPN-01 挑战（因 IIS 占 80 端口）
- Caddyfile路径：`C:\Fyren\caddy\Caddyfile`，已配置 `handle` 块保留下游路径
- `auto_https disable_redirects` + `http_port 9999` 避免与 IIS 冲突
- 安全组已开 TCP 443（`sg-bp10gn3btvuod4p9coge`）
- 测试通过：`openssl s_client` → TLS 1.3 → `{"online":true,...}`
- Caddy 前台运行中 — 需关闭窗口前切到计划任务 `Fyren-Caddy-HTTPS`

### ECS 启动命令（PowerShell引号注意）
```powershell
C:\Fyren\jre-minimal\bin\java "-Djava.net.preferIPv4Stack=true" -cp C:\Fyren\Fyren-1.0-SNAPSHOT.jar com.Fyren.GameMain server 9876 --daemon
C:\Fyren\caddy\caddy.exe run --config C:\Fyren\caddy\Caddyfile
```

### 待办
- [ ] 启用 GitHub Pages（Settings → Pages → master /docs → Save）
- [ ] Caddy 切后台（`Start-ScheduledTask -TaskName "Fyren-Caddy-HTTPS"`）
- [ ] 更新 IIS `C:\inetpub\wwwroot\index.html` 为最新版（当前是旧版，STAT_URL 还是 http）
- [ ] P1 主菜单 UI 实现

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

## P1 待办（优先级顺序）

1. **主菜单/UI** — 标题画面→选角色→匹配→结算→循环（scene2d.ui 或自定义）
2. **背景/视觉美术资源** — 至少一个格斗场景背景
3. **训练模式** — 帧数据显示 + 无对手自由练习
4. **GWT WebSocket 网络对战** — 浏览器端联机

**ECS 部署提醒：** 新 JAR 需部署到 `115.29.230.57` 以启用 P2P 和音效。启动命令：
```cmd
C:\Fyren\jre-minimal\bin\java -Djava.net.preferIPv4Stack=true -cp C:\Fyren\Fyren-1.0-SNAPSHOT.jar com.Fyren.GameMain server 9876 --daemon
```

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
