# GitHub Pages Landing Page Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite `docs/index.html` into a polished tech-portfolio landing page with characters, architecture, code-snippet deep-dives, and a working download flow.

**Architecture:** Single static HTML file with inline CSS and JS. No external dependencies, no images. Progressively built over 7 tasks — each task adds one section and commits.

**Tech Stack:** HTML5, CSS3 (flexbox/grid), vanilla JS (fetch for status API), GitHub Releases API via `gh` CLI.

---

### Task 1: Create GitHub Release with fat JAR

**Files:**
- Create: GitHub Release (via `gh release create`)
- Modify: none

- [ ] **Step 1: Build fat JAR**

```bash
cd D:/develp/Fyren && mvn package -q
```

Expected: `BUILD SUCCESS`, `target/Fyren-1.0-SNAPSHOT.jar` exists.

- [ ] **Step 2: Create GitHub Release**

```bash
cd D:/develp/Fyren && gh release create v1.2 \
  --title "Fyren v1.2 — 帧同步格斗游戏" \
  --notes "## 更新内容

- P2P UDP 打洞（纯 Java NAT 穿透）
- 音效系统（6 个 CC0 WAV 音效）
- GWT WebSocket 跨平台联机（浏览器 vs 桌面同池匹配）
- 打击感五件套（hit-stop/震动/火花/闪烁/残影）
- JWT 双 Token 认证 + Redis 排行榜
- Bug #25 (GWT preloader 路径) + Bug #26 (hit-stop 双重 update) 修复

### 网页体验版
https://uquc.github.io/Fyren/fyren/ — WebGL 双人对战，点开即玩

### 桌面版启动
\`\`\`cmd
java -jar Fyren-1.0-SNAPSHOT.jar      # libGDX demo 模式
java -cp Fyren-1.0-SNAPSHOT.jar com.Fyren.GameMain server 9876  # 启动服务器
java -cp Fyren-1.0-SNAPSHOT.jar com.Fyren.GameMain client <ip> 9876 <id> --preset kage  # 客户端
\`\`\`" \
  target/Fyren-1.0-SNAPSHOT.jar
```

Expected: Release URL `https://github.com/uquc/Fyren/releases/tag/v1.2` created.

- [ ] **Step 3: Commit**

```bash
# The JAR is .gitignored, so this is just a marker commit
git commit --allow-empty -m "ops: create GitHub Release v1.2 with fat JAR"
```

---

### Task 2: HTML Shell — Hero, Status, Demo sections

**Files:**
- Rewrite: `docs/index.html`

**What we build:** The top half of the page — CSS foundation + Hero header + Status bar + Game Demo iframe.

- [ ] **Step 1: Write complete HTML with first 3 sections**

Write `docs/index.html`:

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Fyren — 自研帧同步格斗游戏</title>
<style>
/* === RESET & BASE === */
*, *::before, *::after { margin: 0; padding: 0; box-sizing: border-box; }
html { scroll-behavior: smooth; }
body {
    background: #0a0a0f; color: #e0e0e0;
    font-family: 'Segoe UI', 'Microsoft YaHei', sans-serif;
    line-height: 1.7; min-height: 100vh;
}

/* === TYPOGRAPHY === */
.section-title {
    text-align: center; font-size: 1.4em; color: #ccc;
    letter-spacing: 3px; text-transform: uppercase;
    padding: 80px 0 40px;
}
.section-title span { color: #4ecdc4; }

/* === HERO === */
.hero {
    text-align: center; padding: 60px 20px 30px;
    background: linear-gradient(180deg, #12121f 0%, #0a0a0f 100%);
}
.hero h1 { font-size: 3em; color: #ff6b35; letter-spacing: 8px; font-weight: 700; }
.hero .subtitle { color: #888; margin-top: 10px; font-size: 1.1em; }
.hero .badge {
    display: inline-block; margin-top: 14px; padding: 4px 18px;
    background: #141420; border: 1px solid #1e1e30; border-radius: 20px;
    font-size: 0.82em; color: #666;
}
.hero .tagline {
    max-width: 560px; margin: 20px auto 0; color: #666; font-size: 0.9em; line-height: 1.7;
}

/* === STATUS BAR === */
.status-panel {
    display: flex; gap: 16px; justify-content: center; flex-wrap: wrap;
    padding: 40px 20px 50px; max-width: 1100px; margin: 0 auto;
}
.status-card {
    background: #141420; border: 1px solid #1e1e30; border-radius: 8px;
    padding: 16px 24px; text-align: center; min-width: 110px; flex: 0 0 auto;
    transition: border-color 0.3s;
}
.status-card .label { font-size: 0.7em; color: #555; text-transform: uppercase; letter-spacing: 1.5px; }
.status-card .value { font-size: 1.6em; font-weight: bold; color: #ff6b35; margin-top: 4px; }
.status-card.online { border-color: #4ecdc4; }
.status-card.online .value { color: #4ecdc4; }

/* === GAME DEMO === */
.demo-section { text-align: center; padding: 0 20px 60px; max-width: 1100px; margin: 0 auto; }
.demo-wrapper {
    display: inline-block; border: 1px solid #1e1e30; border-radius: 4px;
    background: #000; overflow: hidden; line-height: 0;
}
.demo-wrapper iframe { width: 960px; height: 540px; border: none; display: block; }
.demo-hint { margin-top: 16px; color: #555; font-size: 0.8em; line-height: 1.7; }
.demo-hint code { background: #141420; padding: 2px 6px; border-radius: 3px; font-size: 0.95em; color: #4ecdc4; }
.demo-hint a { color: #4ecdc4; text-decoration: none; }
.demo-hint a:hover { text-decoration: underline; }

/* === SECTION DIVIDER === */
.section-divider {
    width: 60px; height: 2px; background: #1e1e30; margin: 0 auto;
}

/* === RESPONSIVE === */
@media (max-width: 1000px) {
    .demo-wrapper iframe { width: 100vw; height: calc(100vw * 9 / 16); }
    .status-panel { gap: 10px; padding: 30px 10px 40px; }
    .status-card { padding: 12px 16px; min-width: 80px; }
    .status-card .value { font-size: 1.3em; }
}
</style>
</head>
<body>

<!-- HERO -->
<header class="hero">
    <h1>F Y R E N</h1>
    <p class="subtitle">自研帧同步格斗游戏 · 个人技术作品</p>
    <span class="badge">非商业项目 · 仅供技术交流</span>
    <p class="tagline">
        从帧同步引擎到 P2P 打洞，从打击感到 WebSocket 跨平台联机——<br>一个全栈技术验证项目，所有轮子自己造。
    </p>
</header>

<!-- STATUS BAR -->
<section class="status-panel" id="status-panel">
    <div class="status-card" id="card-status">
        <div class="label">服务器</div>
        <div class="value" id="svr-status">检测中</div>
    </div>
    <div class="status-card">
        <div class="label">运行时长</div>
        <div class="value" id="svr-uptime">--</div>
    </div>
    <div class="status-card">
        <div class="label">在线玩家</div>
        <div class="value" id="svr-players">0</div>
    </div>
    <div class="status-card">
        <div class="label">进行中</div>
        <div class="value" id="svr-matches">0</div>
    </div>
    <div class="status-card">
        <div class="label">累计对战</div>
        <div class="value" id="svr-total">0</div>
    </div>
</section>

<!-- GAME DEMO -->
<div class="section-divider"></div>
<h2 class="section-title" style="padding-top:60px;">&#x25B6; <span>体 验</span></h2>

<section class="demo-section">
    <div class="demo-wrapper">
        <iframe src="fyren/index.html" allowfullscreen
                title="Fyren WebGL Demo"></iframe>
    </div>
    <p class="demo-hint">
        WebGL 双人本地对战 · 点开即玩<br>
        <strong>P1:</strong> <code>WASD</code> 移动 · <code>J</code>拳 <code>K</code>脚 <code>U</code>特殊技 ·
        <strong>P2:</strong> <code>方向键</code> 移动 · <code>1</code>拳 <code>2</code>脚 <code>3</code>特殊技<br>
        联网对战: 访问 <a href="fyren/?mode=network&server=115.29.230.57&playerId=guest">fyren/?mode=network</a>
    </p>
</section>

<script>
var STATUS_URL = 'https://115.29.230.57.nip.io/status';

function fetchStatus() {
    fetch(STATUS_URL)
        .then(function(r) { return r.json(); })
        .then(function(data) {
            document.getElementById('svr-status').textContent = data.online ? '在线' : '离线';
            var card = document.getElementById('card-status');
            card.className = 'status-card' + (data.online ? ' online' : '');
            var h = Math.floor(data.uptimeSeconds / 3600);
            var m = Math.floor((data.uptimeSeconds % 3600) / 60);
            document.getElementById('svr-uptime').textContent = h + 'h ' + m + 'm';
            document.getElementById('svr-players').textContent = data.onlinePlayers;
            document.getElementById('svr-matches').textContent = data.activeMatches;
            document.getElementById('svr-total').textContent = data.totalMatches;
        })
        .catch(function() {
            document.getElementById('svr-status').textContent = '离线';
            document.getElementById('card-status').className = 'status-card';
            document.getElementById('svr-uptime').textContent = '--';
        });
}

fetchStatus();
setInterval(fetchStatus, 10000);
</script>

</body>
</html>
```

- [ ] **Step 2: Verify locally**

Open `docs/index.html` in browser. Verify:
- Hero shows title with orange color, subtitle, badge
- Status cards show 5 cards (server status fetching from ECS)
- Game iframe displays playable game
- Page background is `#0a0a0f`, dark theme

- [ ] **Step 3: Commit**

```bash
git add docs/index.html
git commit -m "feat: landing page shell — Hero + Status + Demo sections"
```

---

### Task 3: Characters Section

**Files:**
- Modify: `docs/index.html` — add Characters section before `</body>`, add CSS to `<style>`

- [ ] **Step 1: Add Character CSS**

Insert before `</style>`:

```css
/* === CHARACTERS === */
.char-section { max-width: 1000px; margin: 0 auto; padding: 0 20px 60px; }
.char-grid {
    display: grid; grid-template-columns: repeat(3, 1fr);
    gap: 20px;
}
.char-card {
    background: #141420; border: 1px solid #1e1e30; border-radius: 8px;
    padding: 28px 24px; position: relative; overflow: hidden;
    transition: border-color 0.3s;
}
.char-card::before {
    content: ''; position: absolute; top: 0; left: 0;
    width: 3px; height: 100%;
}
.char-card.kage::before { background: #6c5ce7; }
.char-card.takeshi::before { background: #e17055; }
.char-card.gou::before { background: #00b894; }

.char-card .char-en { font-size: 1.3em; font-weight: bold; letter-spacing: 3px; color: #ccc; }
.char-card .char-kanji {
    font-size: 2.4em; font-weight: bold; margin: 4px 0 8px; line-height: 1;
}
.char-card.kage .char-kanji { color: #6c5ce7; }
.char-card.takeshi .char-kanji { color: #e17055; }
.char-card.gou .char-kanji { color: #00b894; }

.char-card .char-role {
    font-size: 0.78em; color: #555; text-transform: uppercase; letter-spacing: 2px;
    margin-bottom: 12px;
}
.char-card .char-mechanic { font-size: 0.85em; color: #888; line-height: 1.6; }
.char-card .char-mechanic strong { color: #ccc; }

.char-card .char-bars { margin-top: 16px; display: flex; flex-direction: column; gap: 5px; }
.char-bar-row { display: flex; align-items: center; gap: 8px; font-size: 0.72em; color: #666; }
.char-bar-row span:first-child { width: 30px; text-align: right; }
.char-bar-track {
    flex: 1; height: 4px; background: #1e1e30; border-radius: 2px; overflow: hidden;
}
.char-bar-fill { height: 100%; border-radius: 2px; }
.char-bar-fill.speed { background: #4ecdc4; }
.char-bar-fill.power { background: #ff6b35; }
.char-bar-fill.range { background: #a29bfe; }

@media (max-width: 700px) {
    .char-grid { grid-template-columns: 1fr; }
}
```

- [ ] **Step 2: Add Character HTML**

Insert before the `<script>` tag:

```html
<!-- CHARACTERS -->
<div class="section-divider"></div>
<h2 class="section-title">&#x2694; <span>角 色</span></h2>

<section class="char-section">
    <div class="char-grid">
        <div class="char-card kage">
            <div class="char-en">KAGE</div>
            <div class="char-kanji">影</div>
            <div class="char-role">速攻型 · Rushdown</div>
            <p class="char-mechanic">
                <strong>特殊技取消</strong> — 特殊技可在攻击恢复帧中发动，瞬间取消硬直。<br>
                <strong>CD: 3秒</strong>
            </p>
            <div class="char-bars">
                <div class="char-bar-row"><span>速度</span><div class="char-bar-track"><div class="char-bar-fill speed" style="width:90%"></div></div></div>
                <div class="char-bar-row"><span>力量</span><div class="char-bar-track"><div class="char-bar-fill power" style="width:40%"></div></div></div>
                <div class="char-bar-row"><span>射程</span><div class="char-bar-track"><div class="char-bar-fill range" style="width:50%"></div></div></div>
            </div>
        </div>
        <div class="char-card takeshi">
            <div class="char-en">TAKESHI</div>
            <div class="char-kanji">武</div>
            <div class="char-role">蓄力型 · Brawler</div>
            <p class="char-mechanic">
                <strong>伤害充能</strong> — 累计造成 40 点伤害后，特殊技威力翻倍。<br>
                <strong>重拳压制</strong>
            </p>
            <div class="char-bars">
                <div class="char-bar-row"><span>速度</span><div class="char-bar-track"><div class="char-bar-fill speed" style="width:50%"></div></div></div>
                <div class="char-bar-row"><span>力量</span><div class="char-bar-track"><div class="char-bar-fill power" style="width:85%"></div></div></div>
                <div class="char-bar-row"><span>射程</span><div class="char-bar-track"><div class="char-bar-fill range" style="width:45%"></div></div></div>
            </div>
        </div>
        <div class="char-card gou">
            <div class="char-en">GOU</div>
            <div class="char-kanji">刚</div>
            <div class="char-role">反击型 · Counter</div>
            <p class="char-mechanic">
                <strong>受击充能</strong> — 累计承受 50 点伤害后，特殊技反击威力翻倍。<br>
                <strong>格挡坚韧</strong>
            </p>
            <div class="char-bars">
                <div class="char-bar-row"><span>速度</span><div class="char-bar-track"><div class="char-bar-fill speed" style="width:35%"></div></div></div>
                <div class="char-bar-row"><span>力量</span><div class="char-bar-track"><div class="char-bar-fill power" style="width:70%"></div></div></div>
                <div class="char-bar-row"><span>射程</span><div class="char-bar-track"><div class="char-bar-fill range" style="width:60%"></div></div></div>
            </div>
        </div>
    </div>
</section>
```

- [ ] **Step 3: Verify locally**

Open `docs/index.html` in browser. Verify:
- 3 character cards side by side (single column on narrow screens)
- Kanji characters colored correctly (purple/orange/green)
- Attribute bars show correct proportions
- Left border accent colors match character colors

- [ ] **Step 4: Commit**

```bash
git add docs/index.html
git commit -m "feat: add Characters section — 3 fighter cards with stats"
```

---

### Task 4: Architecture Section

**Files:**
- Modify: `docs/index.html` — add Architecture section

- [ ] **Step 1: Add Architecture CSS**

Insert before `</style>`:

```css
/* === ARCHITECTURE === */
.arch-section { max-width: 1000px; margin: 0 auto; padding: 0 20px 60px; }

/* topology diagram */
.arch-diagram {
    display: flex; flex-direction: column; align-items: center; gap: 0;
    padding: 30px 0 50px;
}
.arch-row { display: flex; gap: 30px; align-items: center; justify-content: center; flex-wrap: wrap; }
.arch-node {
    background: #141420; border: 1px solid #1e1e30; border-radius: 6px;
    padding: 12px 20px; text-align: center; font-size: 0.82em; min-width: 180px;
    transition: border-color 0.3s;
}
.arch-node:hover { border-color: #4ecdc4; }
.arch-node .arch-label { color: #ccc; font-weight: bold; letter-spacing: 1px; }
.arch-node .arch-detail { color: #666; font-size: 0.85em; margin-top: 2px; }
.arch-node.client { border-color: #2a2a4e; }
.arch-node.server { border-color: #ff6b35; }
.arch-node.storage { border-color: #2a2a4e; }

.arch-connector {
    width: 2px; height: 24px; background: #1e1e30; margin: 0 auto;
    position: relative;
}
.arch-connector::after {
    content: ''; position: absolute; bottom: -4px; left: -4px;
    border-left: 6px solid transparent; border-right: 6px solid transparent;
    border-top: 6px solid #1e1e30;
}
.arch-conn-label {
    font-size: 0.65em; color: #555; text-align: center;
    margin: -6px 0 -6px; letter-spacing: 1px;
}

.arch-server-inner {
    background: #141420; border: 2px solid #ff6b35; border-radius: 8px;
    padding: 20px 24px; text-align: center; max-width: 600px; width: 100%;
}
.arch-server-inner .arch-label { font-size: 1em; margin-bottom: 12px; }
.arch-subs {
    display: flex; flex-wrap: wrap; gap: 8px; justify-content: center;
    font-size: 0.75em; color: #777;
}
.arch-subs span {
    background: #0d0d14; border: 1px solid #1e1e30; border-radius: 4px;
    padding: 4px 10px; white-space: nowrap;
}

/* design decisions */
.arch-decisions {
    display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-top: 40px;
}
.arch-decision {
    background: #141420; border: 1px solid #1e1e30; border-left: 3px solid #4ecdc4;
    border-radius: 0 6px 6px 0; padding: 16px 20px;
}
.arch-decision h4 { color: #ccc; font-size: 0.9em; margin-bottom: 4px; letter-spacing: 1px; }
.arch-decision p { color: #777; font-size: 0.8em; line-height: 1.5; }

@media (max-width: 700px) {
    .arch-decisions { grid-template-columns: 1fr; }
}
```

- [ ] **Step 2: Add Architecture HTML**

Insert before `<script>` tag:

```html
<!-- ARCHITECTURE -->
<div class="section-divider"></div>
<h2 class="section-title">&#x25C9; <span>架 构</span></h2>

<section class="arch-section">
    <div class="arch-diagram">
        <!-- Clients row -->
        <div class="arch-row">
            <div class="arch-node client">
                <div class="arch-label">WebGL / GWT</div>
                <div class="arch-detail">浏览器 · WebSocket</div>
            </div>
            <div class="arch-node client">
                <div class="arch-label">libGDX / LWJGL3</div>
                <div class="arch-detail">桌面 · UDP + P2P</div>
            </div>
        </div>

        <div class="arch-connector"></div>
        <div class="arch-conn-label">WS :9878 &nbsp; UDP :9876</div>
        <div class="arch-connector"></div>

        <!-- Server row -->
        <div class="arch-server-inner">
            <div class="arch-label" style="color:#ff6b35;">Game Server (Java 17)</div>
            <div class="arch-subs">
                <span>Matchmaker (ELO)</span>
                <span>FrameSyncManager</span>
                <span>P2P Relay</span>
                <span>WsGameServer</span>
                <span>UdpServer</span>
            </div>
        </div>

        <div class="arch-connector"></div>
        <div class="arch-conn-label">存储 & 鉴权</div>
        <div class="arch-connector"></div>

        <!-- Storage row -->
        <div class="arch-row">
            <div class="arch-node storage">
                <div class="arch-label">Redis</div>
                <div class="arch-detail">用户 · Token · 排行榜</div>
            </div>
            <div class="arch-node storage">
                <div class="arch-label">Auth API :8081</div>
                <div class="arch-detail">JWT 双 Token · bcrypt</div>
            </div>
            <div class="arch-node storage">
                <div class="arch-label">Status API :8080</div>
                <div class="arch-detail">HTTP JSON · 实时状态</div>
            </div>
        </div>
    </div>

    <!-- Key Design Decisions -->
    <div class="arch-decisions">
        <div class="arch-decision">
            <h4>确定性模拟</h4>
            <p>GameWorld.update() 每帧按 playerId 排序输入，不使用浮点 RNG——保证 Java strictfp 下所有客户端计算一致。</p>
        </div>
        <div class="arch-decision">
            <h4>GGPO 式回滚</h4>
            <p>乐观帧锁定，预测执行 + 状态快照。远程输入校验不通过时回滚最多 10 帧，用已确认输入重新模拟。</p>
        </div>
        <div class="arch-decision">
            <h4>P2P 降级策略</h4>
            <p>对称 NAT 下 UDP 打洞自动回退服务器中继。纯 Java 实现，零外部 STUN/TURN 依赖。</p>
        </div>
        <div class="arch-decision">
            <h4>跨协议匹配池</h4>
            <p>UDP 客户端和 WebSocket 浏览器共享同一个 MatchManager 实例。MatchResponseSender 接口解耦传输层。</p>
        </div>
    </div>
</section>
```

- [ ] **Step 3: Verify locally**

Open `docs/index.html` in browser. Verify:
- Topology diagram shows 3 layers (Clients → Server → Storage)
- Server node has orange border and 5 sub-modules listed
- Connector arrows between layers
- 4 design decisions in 2-column grid with teal left border
- Hover effects on topology nodes turn border teal

- [ ] **Step 4: Commit**

```bash
git add docs/index.html
git commit -m "feat: add Architecture section — CSS topology diagram + design decisions"
```

---

### Task 5: Deep Dive Section (4 Module Cards with Code)

**Files:**
- Modify: `docs/index.html` — add Deep Dive section

- [ ] **Step 1: Add Deep Dive CSS**

Insert before `</style>`:

```css
/* === DEEP DIVE === */
.dd-section { max-width: 1000px; margin: 0 auto; padding: 0 20px 60px; }

.dd-card {
    background: #141420; border: 1px solid #1e1e30; border-radius: 8px;
    padding: 28px 28px 20px; margin-bottom: 20px;
    transition: border-color 0.3s;
}
.dd-card:hover { border-color: #4ecdc4; }
.dd-card h3 { color: #4ecdc4; font-size: 1.05em; letter-spacing: 2px; margin-bottom: 2px; }
.dd-card .dd-summary { color: #888; font-size: 0.85em; margin-bottom: 16px; }
.dd-card .dd-bullets { color: #777; font-size: 0.82em; line-height: 1.8; margin-bottom: 20px; padding-left: 16px; }
.dd-card .dd-bullets li { margin-bottom: 2px; }
.dd-card .dd-bullets li::marker { color: #4ecdc4; }

.dd-code {
    background: #0d0d14; border: 1px solid #1e1e30; border-radius: 6px;
    padding: 16px 20px; overflow-x: auto; font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 0.78em; line-height: 1.55; color: #ccc; white-space: pre;
    position: relative;
}
.dd-code::before {
    content: 'Java'; position: absolute; top: -1px; right: 16px;
    background: #1e1e30; color: #555; font-size: 0.7em; padding: 2px 8px;
    border-radius: 0 0 4px 4px; font-family: 'Segoe UI', sans-serif; letter-spacing: 1px;
}
/* syntax highlighting */
.dd-code .kw { color: #4ecdc4; }
.dd-code .str { color: #ff6b35; }
.dd-code .cm { color: #555; font-style: italic; }
.dd-code .nm { color: #a29bfe; }
.dd-code .fn { color: #ffeaa7; }
```

- [ ] **Step 2: Add Deep Dive HTML**

Insert before `<script>` tag:

```html
<!-- DEEP DIVE -->
<div class="section-divider"></div>
<h2 class="section-title">&#x2699; <span>轮 子</span></h2>

<section class="dd-section">

    <!-- Card 1: Frame Sync -->
    <div class="dd-card">
        <h3>帧同步引擎</h3>
        <p class="dd-summary">乐观帧锁定 + GGPO 式回滚 — 格斗游戏网络同步的核心</p>
        <ul class="dd-bullets">
            <li>60fps 固定步长锁步，任意一方输入未到达时预测执行</li>
            <li>每次远程输入到达时逐帧校验预测是否匹配，不匹配则回滚重演</li>
            <li>GameStateSnapshot 每 10 帧保存完整状态快照，最大回滚 10 帧</li>
            <li>gatherInputs() 按 playerId 排序，保证确定性模拟</li>
        </ul>
        <div class="dd-code">// checkAndRollback() — 逐帧校验远程输入，预测错误触发回滚
<span class="kw">private void</span> <span class="fn">checkAndRollback</span>(<span class="kw">int</span> currentFrame) {
    <span class="kw">for</span> (InputBuffer remoteBuf : remoteInputBuffers.values()) {
        <span class="kw">while</span> (confirmedFrame &lt; remoteBuf.getCurrentFrame()) {
            <span class="kw">int</span> checkFrame = confirmedFrame + <span class="nm">1</span>;
            InputCommand confirmed = remoteBuf.getInput(checkFrame);
            <span class="kw">if</span> (confirmed == <span class="kw">null</span>) <span class="kw">break</span>; <span class="cm">// 该帧尚未到达</span>

            InputCommand predicted = lastKnownInputs.get(remotePlayerId);
            <span class="kw">if</span> (predicted != <span class="kw">null</span> &amp;&amp; !predicted.equals(confirmed)) {
                <span class="kw">int</span> rollback = Math.min(currentFrame - confirmedFrame, <span class="nm">10</span>);
                <span class="fn">rollback</span>(currentFrame - rollback); <span class="cm">// 回滚 + 重演</span>
                <span class="kw">return</span>;
            }
            confirmedFrame++;
        }
    }
}</div>
    </div>

    <!-- Card 2: P2P -->
    <div class="dd-card">
        <h3>P2P UDP 打洞</h3>
        <p class="dd-summary">纯 Java NAT 穿透，零外部 STUN/TURN 依赖</p>
        <ul class="dd-bullets">
            <li>匹配成功后双方同时向对方公网地址发送 P2P_PING ×10（20ms 间隔）</li>
            <li>利用路由器建立 NAT 映射表条目，收到对方 P2P_PING 后回复 P2P_PONG</li>
            <li>2 秒超时 → 自动降级为服务器 UDP 中继，对称 NAT 下无缝回退</li>
            <li>打洞成功后 P2P 直连流量完全绕开服务器，零延迟中继开销</li>
        </ul>
        <div class="dd-code">// P2PHandshake — 异步打洞，超时自动降级中继
<span class="kw">public void</span> <span class="fn">start</span>(InetSocketAddress opponentAddr) {
    thread = <span class="kw">new</span> Thread(() -&gt; {
        <span class="kw">long</span> startMs = System.currentTimeMillis();
        <span class="kw">for</span> (<span class="kw">int</span> i = <span class="nm">0</span>; i &lt; <span class="nm">10</span> &amp;&amp; !ready.get(); i++) {     <span class="cm">// PING ×10</span>
            P2pPacket ping = <span class="kw">new</span> P2pPacket(seq++, <span class="str">P2P_PING</span>);
            udpClient.sendRaw(ping.serialize(), opponentAddr);
            Thread.sleep(<span class="nm">20</span>);                             <span class="cm">// 20ms 间隔</span>
        }
        <span class="kw">long</span> deadline = startMs + <span class="nm">2000</span>;               <span class="cm">// 2s 总超时</span>
        <span class="kw">while</span> (System.currentTimeMillis() &lt; deadline &amp;&amp; !ready.get()) {
            Thread.sleep(<span class="nm">50</span>);                             <span class="cm">// 等待 P2P_PONG</span>
        }
        <span class="kw">if</span> (ready.get()) System.out.println(<span class="str">"打洞成功! 直连"</span>);
        <span class="kw">else</span>             System.out.println(<span class="str">"打洞超时，降级中继"</span>);
    }, <span class="str">"P2P-handshake"</span>);
    thread.setDaemon(<span class="kw">true</span>); thread.start();
}</div>
    </div>

    <!-- Card 3: Hit Feedback -->
    <div class="dd-card">
        <h3>打击感五件套</h3>
        <p class="dd-summary">五层反馈叠加 — 命中帧冻结、屏幕震动、粒子火花、受击闪烁、运动残影</p>
        <ul class="dd-bullets">
            <li>Hit-stop 停帧: 命中后冻结渲染，轻击 3 帧 (~0.05s)、重击 6 帧 (~0.1s)，伤害 > 15 判定重击</li>
            <li>屏幕震动: CameraController 偏移叠加衰减正弦波，震幅与伤害正相关</li>
            <li>命中火花: ParticleEffects 在碰撞点生成橙/白色粒子，向两侧飞散并 fade out</li>
            <li>受击闪烁: SpriteRenderer 检测 Fighter.isHitFlag() → 两帧白色覆盖后恢复</li>
            <li>运动残影: MotionTrailEffect 采样角色过去 4 帧位置，半透明重绘产生残影拖尾</li>
        </ul>
        <div class="dd-code">// HitEffects — 命中停帧时长基于伤害量
<span class="kw">public class</span> HitEffects {
    <span class="kw">private float</span> hitStopRemaining = <span class="nm">0f</span>;
    <span class="kw">private static final float</span> HIT_STOP_LIGHT = <span class="nm">0.05f</span>;  <span class="cm">// 3 帧</span>
    <span class="kw">private static final float</span> HIT_STOP_HEAVY = <span class="nm">0.1f</span>;   <span class="cm">// 6 帧</span>

    <span class="kw">public void</span> <span class="fn">onHit</span>(Fighter victim, Fighter attacker, <span class="kw">int</span> damage) {
        hitStopRemaining = Math.max(hitStopRemaining,
            damage &gt; <span class="nm">15</span> ? HIT_STOP_HEAVY : HIT_STOP_LIGHT);
    }

    <span class="kw">public boolean</span> <span class="fn">isInHitStop</span>() {
        <span class="kw">return</span> hitStopRemaining &gt; <span class="nm">0</span>;  <span class="cm">// 调用方应跳过 GameWorld.update()</span>
    }
}</div>
    </div>

    <!-- Card 4: Cross-platform -->
    <div class="dd-card">
        <h3>跨平台联机</h3>
        <p class="dd-summary">浏览器 WebSocket + 桌面 UDP 同池匹配，统一 MMR</p>
        <ul class="dd-bullets">
            <li>MatchManager 通过 MatchResponseSender 接口解耦传输层——不关心客户端是 UDP 还是 WebSocket</li>
            <li>WsGameServer 监听 :9878，接收浏览器 GWT 客户端（JSNI WebSocket, 二进制帧）</li>
            <li>GwtFrameSyncManager 主线程驱动帧循环，无多线程/锁，适配浏览器单线程模型</li>
            <li>桌面客户端 UDP+P2P，浏览器客户端始终服务器中继——两者共享同一 Matchmaker 队列</li>
        </ul>
        <div class="dd-code"><span class="cm">// MatchManager — 传输层无关的匹配响应接口</span>
<span class="kw">public interface</span> MatchResponseSender {
    <span class="kw">void</span> <span class="fn">sendMatchResponse</span>(<span class="kw">int</span> playerId, MatchResponsePacket response,
                           String opponentAddress, <span class="kw">int</span> opponentPort);
}

<span class="cm">// GameServer 同时注入 UDP 和 WebSocket 两种 Sender</span>
matchManager.setMatchResponseSender((playerId, resp, addr, port) -&gt; {
    <span class="kw">if</span> (wsSessions.containsKey(playerId)) {
        wsGameServer.sendMatchResponse(playerId, resp);     <span class="cm">// WebSocket 客户端</span>
    } <span class="kw">else</span> {
        udpServer.sendMatchResponse(playerId, resp, addr);  <span class="cm">// UDP 客户端</span>
    }
});</div>
    </div>

</section>
```

- [ ] **Step 3: Verify locally**

Open `docs/index.html` in browser. Verify:
- 4 module cards stacked vertically
- Each card has teal title, summary line, bullet points, and code block
- Code blocks have dark background (`#0d0d14`) with syntax coloring
- "Java" badge in top-right of each code block
- Hover turns card border teal

- [ ] **Step 4: Commit**

```bash
git add docs/index.html
git commit -m "feat: add Deep Dive section — 4 module cards with syntax-highlighted code"
```

---

### Task 6: Download Section + Footer

**Files:**
- Modify: `docs/index.html` — add Download and Footer, close HTML

- [ ] **Step 1: Add Download/Footer CSS**

Insert before `</style>`:

```css
/* === DOWNLOAD === */
.dl-section { text-align: center; padding: 0 20px 60px; }
.dl-btn {
    display: inline-block; padding: 14px 48px;
    background: #ff6b35; color: #fff; text-decoration: none;
    border-radius: 6px; font-size: 1.05em; font-weight: bold;
    letter-spacing: 1px; transition: background 0.2s, transform 0.15s;
    cursor: pointer; border: none; font-family: inherit;
}
.dl-btn:hover { background: #e55a2b; transform: translateY(-1px); }
.dl-note {
    display: none; margin: 20px auto 0; padding: 16px 24px;
    background: #141420; border-left: 3px solid #ff6b35;
    font-size: 0.78em; color: #888; max-width: 520px;
    text-align: left; line-height: 1.7; border-radius: 0 6px 6px 0;
}
.dl-note button {
    margin-top: 12px; padding: 8px 22px;
    background: #4ecdc4; color: #0a0a0f; border: none;
    border-radius: 4px; cursor: pointer; font-weight: bold; font-size: 0.95em;
}
.dl-note button:hover { background: #3dbdb5; }

/* === FOOTER === */
.site-footer {
    text-align: center; padding: 30px 20px; color: #444; font-size: 0.78em;
    border-top: 1px solid #141420;
}
.site-footer .badges { margin-top: 10px; display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; }
.site-footer .badges span {
    background: #141420; border: 1px solid #1e1e30; border-radius: 4px;
    padding: 2px 10px; font-size: 0.8em; color: #666; letter-spacing: 0.5px;
}
.site-footer a { color: #4ecdc4; text-decoration: none; }
.site-footer a:hover { text-decoration: underline; }
```

- [ ] **Step 2: Add Download and Footer HTML**

Insert before `</body>` (after `<script>` close tag `</script>`):

```html
<!-- DOWNLOAD -->
<div class="section-divider"></div>
<h2 class="section-title">&#x2B07; <span>下 载</span></h2>

<section class="dl-section">
    <button class="dl-btn" onclick="showCompliance()">下载 Windows 版 (JAR)</button>
    <div class="dl-note" id="dl-note">
        <strong>合规分发说明</strong><br>
        本软件为个人技术作品展示，仅供学习交流与技术验证。<br>
        不涉及商业运营，不提供游戏道具购买、虚拟货币充值等服务。<br>
        下载即表示您同意仅将本软件用于个人学习目的。<br>
        <button onclick="proceedDownload()">同意并前往下载</button>
    </div>
</section>

<!-- FOOTER -->
<footer class="site-footer">
    Fyren &copy; 2026 &middot; 个人技术作品 &middot; 非商业项目<br>
    <div class="badges">
        <span>Java 17</span>
        <span>libGDX 1.12</span>
        <span>WebSocket</span>
        <span>JWT</span>
        <span>Redis</span>
        <span>GWT</span>
        <span>Maven</span>
    </div>
    <br>
    <a href="https://github.com/uquc/Fyren">GitHub</a>
</footer>
```

- [ ] **Step 3: Add download JS functions**

Insert before `</script>`:

```javascript
function showCompliance() {
    document.getElementById('dl-note').style.display = 'block';
}

function proceedDownload() {
    window.open('https://github.com/uquc/Fyren/releases/tag/v1.2', '_blank');
}
```

- [ ] **Step 4: Verify locally**

Open `docs/index.html` in browser. Verify:
- Download button visible with orange background
- Click shows compliance note with teal "同意并前往下载" button
- "同意" button opens GitHub Release in new tab
- Footer shows copyright + 7 tech stack badges + GitHub link
- All sections render in order with proper spacing

- [ ] **Step 5: Commit**

```bash
git add docs/index.html
git commit -m "feat: add Download section + Footer with tech badges"
```

---

### Task 7: Push and Verify on GitHub Pages

**Files:**
- Push: `docs/index.html` + all commits

- [ ] **Step 1: Push to GitHub**

```bash
cd D:/develp/Fyren && git push github master
```

Expected: Push succeeds, GitHub Pages auto-deploys (usually < 1 min).

- [ ] **Step 2: Verify live page**

Wait ~30 seconds after push, then check each section:

1. Open `https://uquc.github.io/Fyren/` in browser
2. Hero: title visible with orange "F Y R E N"
3. Status: shows "服务器 在线" with uptime (if ECS is up) or gracefully "离线"
4. Demo: iframe loads playable game
5. Characters: 3 cards with kanji and bars
6. Architecture: topology diagram + 4 decisions
7. Deep Dive: 4 code cards with syntax colors
8. Download: button → compliance → opens Release v1.2
9. Footer: badges and GitHub link

- [ ] **Step 3: Verify Game iframe (separate check)**

Open `https://uquc.github.io/Fyren/fyren/` directly:
- Game loads without errors (check browser console)
- Keyboard controls work (WASD+JKU for P1)
- No 404s on assets

- [ ] **Step 4: Commit verification report**

```bash
git commit --allow-empty -m "verify: GitHub Pages redesign — all 8 sections live"
```

---

## Implementation Complete

After all 7 tasks, the result is:
- 8-section tech portfolio landing page at `https://uquc.github.io/Fyren/`
- GitHub Release v1.2 at `https://github.com/uquc/Fyren/releases/tag/v1.2`
- Working download flow
- Zero external dependencies (no images, no CSS/JS frameworks)
