# 第五讲：跨平台渲染与 GWT 编译

> 源码文件：`FyrenGame.java`、`GameScreen.java`、`FyrenGwtLauncher.java`、`GwtFrameSyncManager.java`、`FyrenGwt.gwt.xml`

---

## 1. 整体架构：一套代码，两个目标

```
                    ┌─── com.Fyren.game/*     (纯 Java，零平台依赖) ──┐
                    │   com.Fyren.sync/*      (输入/帧同步)           │
                    │   com.Fyren.match/*     (匹配逻辑)             │
                    │   com.Fyren.auth/*      (认证——仅桌面端使用)    │
                    │   com.Fyren.redis/*     (Redis——仅桌面端使用)   │
                    │   com.Fyren.render/libgdx/* (渲染——两平台共享)   │
                    │                                                  │
        ┌───────────┴──────────────┐        ┌──────────────────────────┴─────────────┐
        │  桌面端 (LWJGL3)          │        │  浏览器端 (GWT → JavaScript)             │
        │                          │        │                                        │
        │  FyrenLauncher.java      │        │  FyrenGwtLauncher.java                 │
        │  FyrenGame.java          │        │  GwtFrameSyncManager.java              │
        │  GameScreen.java         │        │  GwtNetworkClient.java                 │
        │  FrameSyncManager.java   │        │  GwtWebSocket.java (JSNI)              │
        │  UdpClient.java          │        │                                        │
        │  GameClient.java         │        │                                        │
        │  ↓                       │        │  ↓                                     │
        │  JVM 字节码               │        │  JavaScript (5 permutations)           │
        │  UDP + P2P 直连           │        │  WebSocket (始终服务器中继)             │
        └──────────────────────────┘        └────────────────────────────────────────┘
```

核心思想：**`game/`、`sync/`、`match/`、`render/libgdx/` 四个包两个平台共享。** 平台差异被隔离在特定的平台适配层中。

---

## 2. libGDX 渲染管线

### 2.1 FyrenGame — Screen 状态机

`FyrenGame.java:25-27`：
```java
public enum ScreenState { TITLE, LOGIN, CHAR_SELECT, MATCHING, VS_SPLASH, FIGHT, RESULT, TRAINING }
```

8 个 Screen，由 `switchToScreen()` 统一调度（第 409-448 行），Fade 转场（`startTransition()` / `updateTransition()` / `drawTransitionOverlay()`）：

```java
// Fade 转场：黑屏 alpha 0→1→0，中点切换 Screen
private void updateTransition(float delta) {
    transitionTimer += delta;
    if (transitionTimer >= 0.15f && !midPointDone) {
        midPointDone = true;
        switchToScreen(pendingState);  // 在完全黑屏时切换，用户看不到跳变
    }
}
```

### 2.2 GameScreen — 渲染循环

`GameScreen.java:20-49` — 组件式架构，依赖注入：

```java
public class GameScreen {
    GameWorld gameWorld;              // 逻辑层
    SpriteRenderer spriteRenderer;    // 角色绘制
    HudRenderer hudRenderer;          // 血条/计时器
    HitEffects hitEffects;            // 命中反馈
    ParticleEffects particleEffects;  // 粒子
    MotionTrailEffect motionTrail;    // 残影
    AudioManager audioManager;        // 音效
    BackgroundRenderer background;    // 视差背景
    CameraController camera;          // 摄像机
}
```

每帧执行顺序（在 `renderFight()` 中）：
```
update(delta):
  → 采样输入 → GameWorld.update() → 检测伤害变化
  → HitEffects.update() / ParticleEffects.update() / MotionTrailEffect.sample()
  → 触发音效
  → CameraController.update()

render():
  → BackgroundRenderer.render()    // 4层视差
  → SpriteRenderer.render()        // 角色
  → MotionTrailEffect.render()     // 残影（半透明）
  → ParticleEffects.render()       // 粒子（叠加）
  → HitEffects.render()            // 闪烁/位移
  → HudRenderer.render()           // UI 最上层
```

### 2.3 程序化生成：零外部素材

所有视觉内容由代码生成，不需要美工：

| 组件 | 生成内容 | 技术 |
|------|---------|------|
| `BackgroundRenderer` | 4 层视差背景 | 渐变 + 正弦叠加山形 + 树规则排列 + 随机星点 |
| `SpriteRenderer` | 角色 stick figure | 程序化纹理（椭圆头/矩形躯干/多边形四肢）+ `SpriteBatch` |
| `SoundGenerator` | 6 个 WAV 音效 | 正弦波/白噪声/频率扫频写入 PCM 数据 |

**面试价值**：说明你理解渲染管线的每一层，而不是"拖个 Prefab、绑个材质"。

### 2.4 打击感五件套的协同

五个系统在同一帧内协同工作：

```
CollisionSystem 判定命中
  → HitEffects:    启动 hit-stop 计数器(4-8帧) + 白色闪烁
  → CameraController: 添加震动偏移(振幅=伤害/3, 快速衰减)
  → ParticleEffects:  命中点生成8-12个橙色火花粒子(随机速度+重力)
  → MotionTrailEffect: (如果是冲刺/特殊技)采样角色轮廓
  → AudioManager:   consume 模式读取 Fighter 的音效触发标志
```

**consume 模式**：`Fighter` 设置标志 → 渲染层读取并立即清除。防止同一事件在连续多帧重复触发音效。

---

## 3. GWT 跨平台编译

### 3.1 什么是 GWT？

GWT (Google Web Toolkit) 是一个 Java → JavaScript 的**源码级编译器**。不是把 JVM 塞进浏览器，而是把你的 Java 源码翻译成优化过的 JS。

```java
// Java 源码
public class Hello {
    public String greet(String name) {
        return "Hello, " + name;
    }
}

// GWT 编译后 → JavaScript
function Hello_greet(name) {
    return 'Hello, ' + name;
}
```

### 3.2 GWT 的三个核心限制

**① 没有 `java.net.*`**
`DatagramSocket`、`ServerSocket` 等全部不可用。网络通信只能用浏览器原生 API（WebSocket / XHR）。
→ 解决方案：`GwtWebSocket.java` 通过 JSNI 封装浏览器 `WebSocket`。

**② 没有多线程**
`Thread`、`ExecutorService`、`synchronized` 全部不可用。浏览器是单线程事件循环。
→ 解决方案：`GwtFrameSyncManager` 重写为 `tick(delta)` 主线程驱动。

**③ 没有 AWT/Swing**
`Color`、`Rectangle`、`Graphics2D` 全部不可用。
→ 解决方案：自定义 `Rect` 类，使用 libGDX 的 GWT 兼容 API（`SpriteBatch`、`ShapeRenderer`）。

### 3.3 GWT 编译产物

`gwt-compile.bat` 产生 5 个排列组合（GWT 术语：permutations）：
```
target/gwt-out/fyren/
  ├── fyren.nocache.js        ← 加载器（自动选择正确的 permutation）
  ├── <hash1>.cache.js        ← Chrome/Edge (WebKit)
  ├── <hash2>.cache.js        ← Firefox (Gecko)
  ├── <hash3>.cache.js        ← Safari
  ├── <hash4>.cache.js        ← IE (Trident)
  └── <hash5>.cache.js        ← 其他
```

每个 ~6.8MB（未压缩），这是 GWT 的主要代价——编译产物大。但加载一次后浏览器缓存。

### 3.4 FyrenGwtLauncher — 独立实现的原因

`FyrenGwtLauncher.java:45-48`：
```java
/**
 * 不依赖 FyrenGame.java 或 GameScreen.java（两者都引用 GameClient → java.net.*）。
 * 直接内联 Demo 游戏循环，仅使用 GWT 兼容的依赖。
 */
```

GWT 在编译时检查**所有**依赖。如果 `FyrenGwtLauncher` import 了 `GameClient`，而 `GameClient` import 了 `java.net.DatagramSocket`，编译直接失败。所以 GWT 入口必须是一个独立的、不碰任何 `java.net` 的文件。

### 3.5 WebSocket 网络模式

`FyrenGwtLauncher` 支持两种模式：
```
?mode=demo       → 本地双人对战（默认）
?mode=network    → 联网对战（WebSocket 连接服务器）
```

网络模式初始化（在 `renderNetwork()` 中）：
```java
GwtNetworkClient client = new GwtNetworkClient(serverHost, playerId);
GwtFrameSyncManager syncMgr = new GwtFrameSyncManager(gameWorld);
client.setFrameSyncManager(syncMgr);
```

### 3.6 JSNI — Java 调用 JavaScript

`GwtWebSocket.java` 使用 JSNI (JavaScript Native Interface)：
```java
// 本质上这样写：
native void connect(String url) /*-{
    this.@...socket = new $wnd.WebSocket(url);
    this.@...socket.binaryType = "arraybuffer";
}-*/;
```

浏览器 WebSocket API 返回 `ArrayBuffer`（二进制），符合 Fyren 的二进制协议。服务端 `WsGameServer` 通过 `org.java-websocket` 库处理，与 UDP 共享 `MatchManager`。

---

## 4. 面试追问 & 标准回答

### Q: 为什么选 libGDX 而不是 Unity/Godot？

> 这是一个**技术验证项目**，目的就是理解游戏引擎底层。选 Unity 等于用现成引擎，无法学到"渲染管线怎么搭"、"帧同步怎么实现"。libGDX 是一个薄框架——只提供 OpenGL 封装、输入轮询、资源加载——没有任何游戏逻辑，正好适合从零搭建。

### Q: GWT 编译后的 JS 性能如何？

> 对于 2D 格斗游戏（60fps、两个角色、简单粒子），完全够用。GWT 编译器做积极的死代码消除和内联优化，产生的 JS 比手写通常更快。但如果做 3D 游戏或大量物理计算，GWT 就不合适了。选型匹配需求。

### Q: 为什么浏览器端不支持 P2P？

> 浏览器沙箱不允许直接访问 UDP Socket。WebRTC 理论上可以 P2P（需要 STUN/TURN），但仍有信令服务器依赖。当前实现始终走服务器中继——这是浏览器平台的安全模型决定的，不是技术能力问题。

### Q: 程序化生成和加载 Sprite Sheet 哪个更好？

> 各有场景。Fyren 选程序化生成的原因：(1) 个人项目没有美术资源；(2) stick figure 风格适合程序化（几何图形组合）；(3) 零外部依赖，构建即运行。商业游戏普遍用 Sprite Sheet——美术质量远高于程序化生成，但需要美术管线。坦诚说明取舍原因比吹嘘一种方案更好。

### Q: CJK 字体怎么处理的？

> 桌面端：`FreeTypeFontGenerator` 加载系统字体（Windows 微软雅黑 / macOS PingFang / Linux DroidSansFallback），扫描全项目中的中文字符，只生成这些字符的 Bitmap 纹理（省内存）。GWT 端：使用 GWT 预加载器加载预生成的 `.fnt` + `.png` 字体文件。找不到 CJK 字体时降级为默认 ASCII 字体。

### Q: Fade 转场中的"中点切换 Screen"是什么意思？

> 转场动画是黑屏 alpha 0→1→0（fade out → fade in），总共 300ms。在 alpha=1（完全黑屏）时销毁旧 Screen 并创建新 Screen。这样用户看不到 Screen 销毁/创建过程中的闪烁，感知上就是平滑过渡。
