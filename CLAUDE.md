# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build (JDK 17+, Maven 3.8+)
mvn compile -q
mvn test -q          # JUnit 5 configured, tests in src/test/

# Start server
java -cp target/classes com.Fyren.GameMain server 9876

# Start client (--preset kage|takeshi|gou, default takeshi)
java -cp target/classes com.Fyren.GameMain client <serverIp> [port] [playerId] --preset kage

# Local demo (libGDX window, dual keyboard)
java -cp target/classes com.Fyren.render.libgdx.FyrenLauncher demo --preset kage --preset2 gou

# Fat JAR (shade plugin)
mvn package -q
java -jar target/Fyren-1.0-SNAPSHOT.jar            # libGDX demo
java -cp target/Fyren-1.0-SNAPSHOT.jar com.Fyren.GameMain server 9876
java -cp target/Fyren-1.0-SNAPSHOT.jar com.Fyren.GameMain client <ip> 9876 <id> --preset kage

# jpackage EXE
mvn package -q
jpackage --input target --name Fyren --main-jar Fyren-1.0-SNAPSHOT.jar --main-class com.Fyren.render.libgdx.FyrenLauncher --type exe --win-console --java-options "-XstartOnFirstThread"
```

Dependencies: Lombok (provided), JUnit Jupiter 5.10.2 (test), libGDX 1.12.1 (gdx, gdx-backend-lwjgl3, gdx-platform:natives-desktop).

## Architecture

**Fyren** is a 2D fighting game with UDP networking, hidden-MMR matchmaking, lockstep + rollback frame sync, and libGDX rendering. Java 17+, Maven, single jar for both client and server. ~44 source files.

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
├── match/
│   Matchmaker (ELO + diffusion window), MatchManager (preset forwarding, MMR update on result)
├── util/             InputCodec
├── GameMain.java     — CLI router: server/client/demo modes, --preset parsing
├── GameServer.java   — UdpServer + MatchManager + ResultPacket + HttpStatusServer
└── GameClient.java   — UdpClient + FrameSyncManager + GameWorld, LocalInputProvider injection
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
- **HTTP status API** — HttpStatusServer on port 8080, returns JSON with player count, MMR rankings.
- **World→Screen mapping** — screenX = screenCenter + (worldX - worldCenter) * scale, clamped to margins.

## Known Limitations

- No P2P yet — all input through server relay.
- Swing render files (`SwingGameWindow`, `DemoGameWindow`, `GamePanel`, `StickFigureRenderer`, `KeyInputHandler`) are superseded by libGDX layer but retained for backward compatibility.
- Swing Demo mode uses GamePanel with localPlayerId=1, so "YOU WIN/LOSE" overlay may be wrong for P2 perspective.
- No sound effects loaded (AudioManager is skeleton only).
- No background art (black background with procedural ground/grid lines).
- `float` coordinates — fine for single-platform (Java strictfp), not cross-platform deterministic.
- FrameSyncManager's `predictInputs()` has a bug: when local input is null, it iterates remoteInputBuffers to find missing inputs but doesn't handle the case correctly for local player.
- GWT/WebGL target compiles but `gdx-backend-gwt` dependency not in pom.xml (separate build step needed).

## ECS Deployment (Task 11 — DONE 🎉)

**Instance:** `i-bp10gn3btvuod4p2dpha`, cn-hangzhou
- **Public IP:** 115.29.230.57
- **Private IP:** 172.16.220.228
- **OS:** Windows Server 2022 Datacenter (64-bit)
- **Password:** Fyren@2026!Server (Administrator)

**Security Group** (`sg-bp10gn3btvuod4p9coge`):
- TCP 80 (HTTP — portfolio website)
- TCP 8080 (HTTP status API)
- UDP 9876 (game server)
- TCP 22 (SSH)
- ALL ICMP
- TCP 3389 (RDP)

**Running Services:**
| Service | Port | Status |
|---------|------|--------|
| Portfolio website | 80 | ✅ |
| HTTP status API | 8080 | ✅ |
| Game server (UDP) | 9876 | ✅ |

**Deployment method:** RDP file transfer + jlink minimal JRE (java.base, java.logging, java.net.http, java.management, jdk.unsupported, jdk.httpserver)
- `C:\Fyren\` — fat JAR + JRE
- `C:\inetpub\wwwroot\index.html` — IIS static site
- Daemon mode (`--daemon` flag) + shutdown hook for graceful stop
- Firewall rules configured via netsh

## Data Flow

```
libGDX mode (current production):
  Keyboard → GdxInputHandler (Gdx.input polling)
    → InputCommand
    → GameScreen.updateDemo() / updateNetwork()
    → GameWorld.update() → CollisionSystem
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

Swing mode (legacy, retained):
  Keyboard → KeyInputHandler → InputCommand
  Swing Timer → GamePanel.repaint() → StickFigureRenderer
```

## Current Session (2026-06-05)

**Phases completed:** 5 of 5 (Tasks 1-12 of 12) — ALL DONE 🎉

Last commit: `494ab29 feat: 渲染层+战斗系统+Bug修复`
**Uncommitted changes:** GameServer.java (--daemon), web/index.html (IP placeholder), deploy-ecs.ps1 (new), + render/libgdx/*, network/*, match/*, pom.xml, etc.

### Task Status

| # | Task | Status |
|---|------|--------|
| 1 | Dash Bug 修复 | ✅ done |
| 2 | Maven + libGDX 骨架 | ✅ done |
| 3 | GameScreen + GdxInputHandler + CameraController | ✅ done |
| 4 | SpriteRenderer | ✅ done |
| 5 | HUD + 特效系统 | ✅ done |
| 6 | FyrenGame 集成 + GameMain 新入口 | ✅ done |
| 7 | GWT/WebGL 构建目标 | ✅ done |
| 8 | 服务端 HTTP 状态 API | ✅ done |
| 9 | 作品展示网站 | ✅ done |
| 10 | EXE 打包 (jpackage) | ✅ done |
| 11 | ECS 部署 | ✅ done |
| 12 | 品质打磨 | ✅ done |
| 10 | EXE 打包 (jpackage) | ✅ done |
| 11 | ECS 部署 | 🔄 in progress |
| 12 | 品质打磨 | ✅ done |

### Plan file
`.claude/plans/cheeky-tickling-twilight.md`

### Uncommitted files (~44 modified/new)
All libGDX render files under `render/libgdx/`, plus modified `pom.xml`, `GameMain.java`, `GameServer.java`, `GameClient.java`, network/match files, and new `web/index.html`.
