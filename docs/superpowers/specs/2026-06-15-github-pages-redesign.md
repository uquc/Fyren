# GitHub Pages Landing Page Redesign

**Date:** 2026-06-15
**Author:** developer Claude
**Status:** designed, awaiting spec review

## Goal

Redesign `docs/index.html` (GitHub Pages at `https://uquc.github.io/Fyren/`) from a basic product page into a polished personal-tech-portfolio landing page. Also fix the download flow by creating a GitHub Release.

## Scope

**In scope:**
- `docs/index.html` — full redesign, single static HTML file (inline CSS + JS)
- GitHub Release creation — fat JAR attachment for download

**Out of scope:**
- ECS server recovery (already done — server back online, status API live)
- GWT game logic changes
- Multi-page site
- External image dependencies (everything is CSS + code text)

## Page Structure (Top → Bottom)

| # | Section | Key Content |
|---|---------|-------------|
| 1 | Hero | Title "F Y R E N", subtitle, tech-badge |
| 2 | Status Bar | Server online/offline, uptime, player count (fetched from ECS) |
| 3 | Game Demo | iframe embed, control hints, network mode link |
| 4 | Characters | 3 horizontal cards: KAGE(影)/TAKESHI(武)/GOU(刚) |
| 5 | Architecture | CSS-drawn topology diagram + 4 key design decisions |
| 6 | Deep Dive | 4 module cards with code snippets |
| 7 | Download | Button → GitHub Release, compliance note |
| 8 | Footer | Copyright + tech stack badges |

## Visual Design

### Palette
| Token | Hex | Usage |
|-------|-----|-------|
| bg | `#0a0a0f` | Page background |
| card-bg | `#141420` | Card surfaces |
| border | `#1e1e30` | Card borders, separators |
| accent-game | `#ff6b35` | Game/character elements, orange emphasis |
| accent-tech | `#4ecdc4` | Architecture, code, module cards |
| text-primary | `#e0e0e0` | Body copy |
| text-secondary | `#888` | Descriptions, hints |
| code-bg | `#0d0d14` | Code block background |

### Typography
- Headers: uppercase, `letter-spacing: 2-4px`, English/Chinese mixed
- Body: `'Segoe UI', 'Microsoft YaHei', sans-serif`, `line-height: 1.7`
- Code: `'Consolas', 'Monaco', monospace` with syntax highlighting (keywords `#4ecdc4`, strings `#ff6b35`, comments `#555`)

### Spatial Rhythm
- Section gap: `80-120px` padding
- Card internal: `24-32px` padding
- Cards layout: 3-column grid → single column on mobile (max-width breakpoint)
- Architecture diagram: CSS flexbox nodes with `::before`/`::after` connector lines

### Vibe
Clean tech-blog minimalism meets fighting-game impact. No superfluous decorations, shadows, or gradients — color and whitespace do the work.

## Section Details

### 1. Hero
- Large "F Y R E N" with wide letter spacing
- Subtitle: "自研帧同步格斗游戏 · 个人技术作品"
- Badge: "非商业项目 · 仅供技术交流"
- (Minor tweaks from existing, mostly preserved)

### 2. Status Bar
- Keep existing 5-card layout
- Fetch from `https://115.29.230.57.nip.io/status` (Caddy HTTPS proxy); fallback to `http://115.29.230.57:8080/status` for local testing
- **Known limitation:** Mixed content (HTTPS page → HTTP API) will be blocked by browsers. If Caddy SSL is broken, status will gracefully show "离线". The status panel is a nice-to-have, not critical.
- Graceful fallback: all cards show "离线"/"--"/"0" on fetch failure
- Refresh every 10s

### 3. Game Demo
- Keep existing iframe embed (`docs/fyren/index.html`)
- Control hints: P1 WASD+JKU, P2 Arrow+123
- Add network mode link mention

### 4. Characters — 3 Cards
Each card shows:
- Character name (EN): KAGE / TAKESHI / GOU
- Kanji: 影 / 武 / 刚
- Role tagline: 速攻 / 蓄力 / 反击
- Special mechanic description
- Visual: colored accent left border (blue-ish / red-orange / green-teal)

No images — pure CSS with colored blocks and text.

### 5. Architecture — Two Sub-sections

**Top: Topology Diagram (CSS-only)**
```
🌐 Browser (WebGL/GWT)          🖥️ Desktop (libGDX/UDP)
        │ WebSocket                    │ UDP + P2P
        ▼                              ▼
        ⚙️ Game Server (Java)
          ├── Matchmaker (ELO + window)
          ├── FrameSyncManager (lockstep + rollback)
          ├── P2P Relay (hole-punch fallback)
          ├── WsGameServer (WS :9878)
          └── UdpServer (UDP :9876)
        │                              │
        ▼                              ▼
   🗄️ Redis + Auth API (JWT)     📊 HttpStatusServer
```

Each node is a CSS-styled `<div>` with borders; connector lines via `::before`/`::after` pseudo-elements or thin borders. All text, no images.

**Bottom: 4 Key Design Decisions**
Two-column grid, each item format:
> **Decision title** — one-line summary
> Brief rationale (1 sentence)

Content:
1. 确定性模拟 — GameWorld.update() 按 playerId 排序输入，无浮点 RNG
2. Rollback 网码 — 乐观锁步 + GGPO 式回滚，最大 10 帧预测窗口
3. P2P 降级 — 对称 NAT 自动回退服务器中继，无单点故障
4. 跨平台匹配池 — UDP + WebSocket 客户端共享 MatchManager，统一 MMR

### 6. Deep Dive — 4 Module Cards

Each card structure:
```
┌──────────────────────────────────────────┐
│  🔧 Module Name                          │
│  One-line summary                        │
│  ──────────────────────────────────       │
│  Technical details (2-4 bullet points)    │
│  ──────────────────────────────────       │
│  [Code snippet, 5-15 lines]              │
└──────────────────────────────────────────┘
```

**Card 1: 帧同步引擎 (FrameSyncManager)**
- Summary: 乐观帧锁定 + GGPO 式回滚
- Detail bullets: 60fps 锁步, 10f 预测窗口, GameStateSnapshot 快照, 确定性输入排序
- Code: `checkAndRollback()` — remote input verification + prediction mismatch → rollback

**Card 2: P2P UDP 打洞 (P2PHandshake)**
- Summary: 纯 Java NAT 穿透，零外部依赖
- Detail bullets: PING×10 (20ms间隔), 2s 超时降级中继, 对称 NAT 自动 fallback
- Code: Punch loop — send P2P_PING + await P2P_PONG with timeout

**Card 3: 打击感五件套 (HitEffects + system)**
- Summary: 五层打击反馈系统
- Detail bullets: hit-stop 停帧, 屏幕震动, 命中火花, 受击闪烁, 运动残影
- Code: HitEffects — damage-based hit-stop duration

**Card 4: 跨平台联机 (MatchManager + WsGameServer)**
- Summary: 浏览器与桌面客户端同池匹配
- Detail bullets: transport-agnostic MatchResponseSender, JSNI browser WebSocket, shared match pool
- Code: MatchResponseSender interface — decoupled from transport

### 7. Download
- Button: "下载 Windows 版 (JAR)"
- Click → show compliance note → agree → link to GitHub Release
- GitHub Release to be created with fat JAR attached

### 8. Footer
- "Fyren © 2026 · 个人技术作品 · 非商业项目"
- Tech stack badges (Java 17, libGDX, WebSocket, JWT)

## Implementation Plan

### Phase 1: GitHub Release
1. Build fat JAR: `mvn package -q`
2. Create GitHub Release via `gh release create`
3. Attach `target/Fyren-1.0-SNAPSHOT.jar`
4. Update download link in page

### Phase 2: Single HTML Rewrite
1. Rewrite `docs/index.html` with all sections
2. Inline CSS for all styling (no external deps)
3. Inline JS for status fetch + compliance flow
4. Test locally by opening file in browser

### Phase 3: Verify
1. Push, wait for GitHub Pages deploy
2. Verify all sections render correctly
3. Verify status API fetches live data
4. Verify download flow works
5. Verify game iframe loads

## Files Changed
- `docs/index.html` — complete rewrite
- (No other files touched)

## Success Criteria
- [ ] Page loads without errors
- [ ] All 8 sections visible and styled correctly
- [ ] Status cards show real ECS data when server is up (local test); graceful "离线" on GitHub Pages HTTPS (mixed content limitation)
- [ ] Game iframe loads and is playable
- [ ] Download button links to a real GitHub Release
- [ ] Mobile layout is readable (cards stack vertically)
- [ ] No external image/CSS/JS dependencies
