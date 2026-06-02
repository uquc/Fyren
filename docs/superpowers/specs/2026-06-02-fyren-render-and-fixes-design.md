# Fyren 2D格斗游戏 — 渲染层 + 战斗系统 + Bug修复 设计文档

**日期:** 2026-06-02
**状态:** 待审批

---

## 1. 概述

在现有网络层(UDP)、匹配层(MMR)、同步层(帧同步)基础上，修复关键bug，实现Java2D火柴人渲染和完整战斗系统，使游戏demo可玩可看。

### 1.1 现有代码量

~2600行 Java，Maven + Lombok，包结构 `com.Fyren.{game,network,sync,match,util}`

### 1.2 不做的事

- 网络层、匹配层、GameWorld/CollisionSystem 不动
- 不引入Docker（留到后期服务器部署时加）
- 不实现P2P直连（当前走服务器中转，够用）
- 不实现骨骼动画
- 没有跳跃（↑改为投技）

---

## 2. Bug修复

### Bug #1: GameClient.startGame() FrameSyncManager 重复创建

**文件:** `GameClient.java:150-173`

**问题:** 第158行创建第一个 FrameSyncManager，第161行用匿名子类覆盖，前一个实例泄漏。

**修复:**
```java
public void startGame() {
    if (state != ClientState.MATCHED) { ... return; }
    setState(ClientState.PLAYING);
    frameSyncManager = new FrameSyncManager(gameWorld);
    frameSyncManager.setLocalInputProvider(this::getCurrentLocalInput);
    frameSyncManager.start();
    if (callback != null) callback.onGameStart();
}
```

移除匿名子类。`frameCounter` 改为由 FrameSyncManager 内部管理，`submitInput()` 不再自增帧号。

### Bug #2: FrameSyncManager.collectLocalInput() 空实现

**文件:** `FrameSyncManager.java:92-97`

**修复:** 增加 `LocalInputProvider` 函数式接口和 setter，由 GameClient 注入：
```java
@FunctionalInterface
public interface LocalInputProvider {
    InputCommand getInput(int frameNumber, int localPlayerId);
}
```

### Bug #3: 渲染循环缺失

**修复:** 不在 FrameSyncManager 的 gameLoop 中调用 render()。改为 Swing Timer 驱动渲染，从 GameWorld 读取只读状态绘制。

---

## 3. 新增组件

### 3.1 SwingGameWindow
**包:** `com.Fyren.render`
**职责:** JFrame 窗口管理，启动 Swing Timer(~16ms/60fps)，组合 GamePanel 和 KeyInputHandler。

构造: `SwingGameWindow(GameClient client, int localPlayerId, FighterPreset preset)`

尺寸: 960×540（16:9 格斗游戏比例）

### 3.2 GamePanel
**包:** `com.Fyren.render`
**职责:** 重写 `paintComponent(Graphics g)`，从 GameClient.getGameWorld() 读取状态，调用 StickFigureRenderer 绘制。显示计时器、双方血量条。

### 3.3 StickFigureRenderer
**包:** `com.Fyren.render`
**职责:** 纯静态工具方法，根据 Fighter 属性和姿态枚举绘制火柴人。

公开方法:
- `drawFighter(Graphics2D g, Fighter f, FighterPreset p, FighterStance stance, boolean facingRight)` — 主绘制入口
- `drawHealthBar(Graphics2D g, int x, int y, int health, int maxHealth)` — 血量条
- `drawAttackEffect(Graphics2D g, float x, float y, float width, float height)` — 攻击框可视化（调试用）
- `drawTimer(Graphics2D g, int remainingSeconds)` — 倒计时

### 3.4 KeyInputHandler
**包:** `com.Fyren.render`
**职责:** 实现 `KeyListener`，维护按键状态位掩码，每帧采样生成 InputCommand。检测双击输入(←←/→→)触发冲刺。

按键映射:
| 键 | 动作 |
|---|------|
| W | 投技（破防） |
| S | 防御 |
| A | 后退 |
| D | 前进 |
| J | 拳 |
| K | 脚 |
| U | 特殊技 |

---

## 4. 角色系统

### 4.1 FighterPreset 枚举（`com.Fyren.game`）

| 属性 | 影 (KAGE) | 武 (TAKESHI) | 刚 (GOU) |
|------|:---------:|:------------:|:--------:|
| 血量 | 80 | 100 | 130 |
| 基础伤害 | 15 | 10 | 12 |
| 攻击距离 | 45px | 50px | 60px |
| 前进速度 | 5.5 | 4.0 | 2.8 |
| 后退速度 | 2.2 | 1.8 | 1.2 |
| 受击框(宽×高) | 40×90 | 50×100 | 60×110 |
| 冲刺-前 | 120px | 90px | 70px |
| 冲刺-后 | 70px | 50px | 40px |
| 线条宽度 | 2px | 3px | 5px |
| 线条颜色 | 深蓝 #2255CC | 红 #CC3333 | 墨绿 #2D6A4F |
| 头身比 | 小头瘦身 | 标准 | 大头宽身 |

**约束:** 最快后退(2.2) < 最慢前进(2.8) ✓；后退冲刺 ≈ 前进冲刺55-60% ✓

### 4.2 Fighter 改造

构造: `Fighter(int id, float x, float y, FighterPreset preset)`
新增: `getPreset()`、`getStance()`、`isInStun()`、`getStunRemaining()`
Fighter内部持有当前动作状态机和帧计数器。

### 4.3 火柴人姿态（FighterStance 枚举）

| 姿态 | 触发条件 | 视觉效果 |
|------|---------|---------|
| IDLE | 无输入 | 标准站立 |
| WALK_FORWARD | D按下，onGround | 身体前倾，前腿微曲 |
| WALK_BACKWARD | A按下，onGround | 身体后倾 |
| PUNCH | J按下，前摇/判定/后摇中 | 前手前伸+拳头圆点 |
| KICK | K按下，动作帧中 | 前腿抬起+脚部加长 |
| THROW | W按下，动作帧中 | 双手前伸抓取 |
| SPECIAL | U按下，动作帧中 | 双手前伸+颜色特效圈 |
| BLOCK | S按下 | 身体收缩，双臂交叉 |
| HURT | isInStun() | 身体后仰，红色闪烁 |
| DASH | 冲刺中 | 身体倾斜+速度线 |

---

## 5. 战斗系统

### 5.1 基础规则

- 游戏速率: 60fps（1帧 ≈ 16.67ms）
- 对局时间: 99秒倒计时，时间到血量多者胜
- 胜负判定: 血量归零或时间结束时血量较少者败；双方同血量则平局
- 角色选择: 启动参数 `--preset kage|takeshi|gou`，默认武
- 地面Y坐标: GROUND_Y = 100（所有角色始终在地面，无跳跃）

### 5.2 动作帧数据

所有攻击动作分三段：**前摇 → 判定帧 → 后摇**。后摇期间不可做其他动作（除影的特殊技可取消后摇）。

| 动作 | 前摇(帧) | 判定(帧) | 后摇(帧) | 伤害 | 备注 |
|------|:--:|:--:|:--:|:--:|------|
| 拳(J) | 3 | 3 | 5 | 基础伤害 | 最快，距离中等 |
| 脚(K) | 5 | 3 | 7 | 基础+2 | 稍慢，距离略远 |
| 投技(W) | 4 | 2 | 6 | 基础-3 | **破防**，近身距离 |
| 防御(S) | 即时 | 持续 | 即时 | — | 减伤50%，降低移速 |
| 冲刺 | 即时 | 8 | 即时 | — | 冲刺中出招前摇减半、伤害-5 |

### 5.3 特殊技

| | 影·影袭 | 武·气合掌 | 刚·地震脚 |
|---|---|---|---|
| 伤害 | 12 | 14 | 18 |
| 限制类型 | **CD冷却** | **造成伤害累计** | **受到伤害累计** |
| 阈值 | 3秒CD | 累计造成40伤害 | 累计受到50伤害 |
| 前摇/判定/后摇 | 2/2/4帧 | 4/3/5帧 | 6/4/6帧 |
| 额外效果 | 向前突进60px | 击退敌人50px | 近身AOE(80px范围) |
| 特殊机制 | 可取消后摇 | 命中+5帧额外僵直 | 不可防御 |

- 影的CD在特殊技释放后开始冷却，3秒后可用
- 武的伤害计数器累计所有来源造成的伤害（拳/脚/投技/特殊技/冲刺攻击）
- 刚的受到伤害计数器累计所有来源的受伤值，防御减免后的实际伤害也计数

### 5.4 冲刺系统

- **触发:** ←← 或 →→（200ms内同方向双击）
- **存储上限:** 3次
- **回复:** 固定3秒CD回复1次
- **效果:** 冲刺方向快速突进8帧，期间出招前摇减半、伤害-5
- 后退冲刺距离 < 前进冲刺距离（见角色表）

### 5.5 投技与防御

- **投技(W):** 攻击框为贴身短矩形，若与对方受击框重合 → 破防，全额伤害+僵直
- **防御(S):** 受到拳脚攻击时伤害减半(50%)，投技无视减免（全额伤害）
- 防御状态下投技额外+2帧僵直

### 5.6 僵直系统

- 被攻击命中 → 进入僵直状态，不可行动
- 僵直帧数 = 5 + (受到伤害 / 5)，上限20帧
- 僵直期间受击框保持，不可防御、不可行动

### 5.7 攻击相遇

采用 **相杀制**：同帧双方攻击框相交 → 各自受伤害（伤害减半），双方都进入短僵直（固定3帧）。

### 5.8 判定框系统

```
受击框 (hitbox):   Rectangle(centerX - w/2, y - h, w, h)
                   始终存在，w/h由角色FighterPreset决定
                   
攻击框 (attackBox): 仅在前摇结束后的"判定帧"期间存在
                    不同动作攻击框位置/大小不同，由朝向决定偏移方向
```

| 动作 | 攻击框 (宽×高) | 相对位置 |
|------|:--:|------|
| 拳 | 50×30 | 角色前方，距离=角色攻击距离参数 |
| 脚 | 35×25 | 角色前方+15px偏移(比拳远) |
| 投技 | 30×20 | 贴身前方 |
| 特殊·影袭 | 60×30 | 突进全程判定 |
| 特殊·气合掌 | 40×35 | 前方击退方向 |
| 特殊·地震脚 | 80×25 | 角色中心AOE |

**命中条件:** 攻击方攻击框 ∩ 防御方受击框 ≠ ∅，且仅判定帧期间

---

## 6. 特殊技资源管理

Fighter 内部新增:
- `specialCooldownRemaining` — 影的CD剩余帧数
- `damageDealtSinceLastSpecial` — 武的累计造成伤害
- `damageTakenSinceLastSpecial` — 刚的累计受到伤害
- `isSpecialReady()` — 特殊技是否可用
- `onSpecialUsed()` — 释放后重置计数器
- `onDamageDealt(int dmg)` / `onDamageTaken(int dmg)` — 战斗系统调用

---

## 7. 数据流

```
键盘事件 ──→ KeyInputHandler (维护按键状态+检测双击)
                    │
  Timer(16ms) ──→ 采样 → InputCommand (含冲刺标记)
                    │
         ┌──────────┼──────────┐
         ▼                     ▼
  UdpClient.sendUnreliable   FrameSyncManager.localInputQueue
  (发给对手)                  (本地帧同步)
         │                     │
         ▼                     ▼
  UdpServer转发          FrameSyncManager.gameLoop
         │                gatherInputs → GameWorld.update()
         ▼                     │
  对方FrameSyncManager         ▼
                       GameWorld 状态更新完毕
                       (含战斗系统: 判定框/僵直/伤害/特殊技资源)
                              │
  Swing Timer(~16ms) ──→ repaint() → GamePanel.paintComponent()
                              │
          StickFigureRenderer.draw(f1, f2, timer, ui)
```

---

## 8. 文件变更清单

### 修改的文件

| 文件 | 改动摘要 |
|------|---------|
| `GameClient.java` | 修复startGame()双创建；增加currentLocalInput；submitInput不再自增帧号；增加preset参数 |
| `FrameSyncManager.java` | 增加LocalInputProvider接口；collectLocalInput改用注入provider |
| `Fighter.java` | 重构：接收FighterPreset；增加动作状态机(前摇/判定/后摇/僵直)；判定框方法；特殊技资源管理 |
| `CollisionSystem.java` | 增加判定框相交检测（替换旧的简单矩形检测） |
| `GameWorld.java` | 增加计时器；回合结束判定；伤害/僵直处理逻辑 |
| `GameMain.java` | 客户端模式解析--preset参数；创建SwingGameWindow替代控制台输入 |

### 新增的文件

| 文件 | 包 | 行数估算 |
|------|-----|---------|
| `FighterPreset.java` | `com.Fyren.game` | ~100 |
| `FighterStance.java` | `com.Fyren.game` | ~40 |
| `SwingGameWindow.java` | `com.Fyren.render` | ~120 |
| `GamePanel.java` | `com.Fyren.render` | ~100 |
| `StickFigureRenderer.java` | `com.Fyren.render` | ~250 |
| `KeyInputHandler.java` | `com.Fyren.render` | ~80 |

### 不动

- 所有 `com.Fyren.network.*`
- 所有 `com.Fyren.match.*`
- `com.Fyren.sync.InputBuffer`、`InputCommand`、`InputCodec`
- `GameStateSnapshot`（需补充新字段：计时器、特殊技资源状态）
- `GameServer`、`UdpServer`

---

## 9. 验证标准

1. 服务器 + 两个客户端启动后出现渲染窗口，显示双方火柴人
2. WASD 移动：前进速度快于后退，Y坐标始终在地面
3. ←←/→→ 触发冲刺，存储3次、3秒CD回复，后退冲刺短于前进
4. J/K/W/U 出招有前摇/判定/后摇，帧数据正确
5. ↑(投技)命中防御中敌人 → 破防全额伤害
6. 攻击命中产生僵直，持续时间受伤害影响
7. 双方同时出招 → 相杀（各受半伤+短僵直）
8. 影3秒CD、武40伤害累计、刚50受伤累计 → 特殊技可用
9. 99秒倒计时结束 → 血量多者胜
10. 血量归零 → 游戏结束，双方显示结果
11. `--preset kage` vs `--preset gou` → 不同体型/颜色/属性正确渲染

---

## 10. 未来扩展点（仅记录，不实现）

- **登录模块:** `ClientSession` 预埋 `authenticated` 字段，后续加 `AuthPacket`+`AuthManager`
- **P2P直连:** `MatchResponsePacket` 已携带对端地址，客户端需增加直连逻辑
- **Docker部署:** 服务器端加 `Dockerfile`（`eclipse-temurin:21-jre`），5行
- **定点数:** `Fighter` 中的 `float` 改为定点数以跨平台确定性（Java `strictfp` 已保证单平台一致）
- **角色选择界面:** 后续迭代加UI选择，当前用启动参数
- **浮动伤害数字:** 命中时显示伤害数值
- **音效:** 攻击/命中/防御音效反馈
