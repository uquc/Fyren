# Fyren 面试准备 — 技术深讲

> 五讲覆盖简历中所有技术点。每讲含源码精读、设计原理解析、面试追问与标准回答。

## 目录

| # | 讲题 | 核心文件 | 预计时间 |
|---|------|---------|---------|
| [01](01-帧同步与回滚网络.md) | 帧同步与回滚网络 | `FrameSyncManager`, `GwtFrameSyncManager`, `InputBuffer`, `InputCommand`, `GameStateSnapshot` | 30min |
| [02](02-网络传输层-UDP可靠传输与P2P打洞.md) | UDP 可靠传输与 P2P 打洞 | `Packet`, `UdpClient`, `UdpServer`, `P2PHandshake`, `P2pPacket` | 25min |
| [03](03-匹配系统与JWT认证.md) | 匹配系统与 JWT 认证 | `Matchmaker`, `MatchManager`, `JwtTokenProvider`, `AuthService`, `RedisService` | 25min |
| [04](04-确定性战斗核心.md) | 确定性战斗核心 | `Fighter`, `GameWorld`, `CollisionSystem`, `GameStateSnapshot` | 20min |
| [05](05-跨平台渲染与GWT编译.md) | 跨平台渲染与 GWT 编译 | `FyrenGame`, `GameScreen`, `FyrenGwtLauncher`, `GwtFrameSyncManager` | 20min |

## 使用建议

1. **第一遍**：按顺序通读，跟着源码链接看代码
2. **第二遍**：只看每讲最后的"面试追问"，尝试不看答案自己回答
3. **第三遍**：对着简历简介，逐句找到对应的源码和讲解

## 面试中最可能被深挖的 5 个点

1. **回滚机制** → 第一讲 §4 — "预测错误后怎么恢复？为什么上限 10 帧？"
2. **双信道设计** → 第二讲 §3 — "为什么不用 TCP？可靠和不可靠怎么划分？"
3. **P2P 打洞** → 第二讲 §5 — "对称 NAT 怎么办？怎么降级？"
4. **扩散窗口** → 第三讲 §2 — "为什么不是固定分差匹配？扩散速度怎么定？"
5. **确定性保证** → 第四讲 §1 — "怎么保证两台机器状态一致？浮点数安全吗？"

## 简历简介 ↔ 源码映射

| 简历描述 | 对应讲 | 关键源码 |
|---------|--------|---------|
| "STARTUP→ACTIVE→RECOVERY→IDLE 四阶段动作流转" | 第四讲 §2 | `Fighter.startAction()` / `updateAction()` |
| "锁步同步、投机执行、快照回滚" | 第一讲 §3-4 | `FrameSyncManager.predictInputs()` / `rollback()` |
| "ACK应答+数据包重传" | 第二讲 §3 | `UdpClient.sendReliable()` / `checkRetransmit()` |
| "P2P UDP NAT穿透" | 第二讲 §5 | `P2PHandshake.start()` |
| "WebSocket双栈传输，跨平台互通" | 第二讲 §4 + 第五讲 §3 | `WsGameServer` / `GwtWebSocket` |
| "JWT双Token认证" | 第三讲 §5-8 | `JwtTokenProvider` / `AuthService.refresh()` |
| "Redis降级内存模式" | 第三讲 §10-11 | `RedisService.init()` / 各 `if-available-else-memory` |
| "ELO评分+扩散窗口" | 第三讲 §1-2 | `Matchmaker.processMatches()` / `MatchEntry.getAllowedDiff()` |
| "GWT编译为JavaScript" | 第五讲 §3 | `FyrenGwtLauncher` / `FyrenGwt.gwt.xml` |
| "4层视差背景+打击感五件套" | 第五讲 §2 | `BackgroundRenderer` / `HitEffects` + `ParticleEffects` |
| "HTTP热部署" | 未单独成讲 | `AuthHttpServer` `/admin/deploy` 端点 |
