# GWT WebSocket 网络对战 — 设计文档

**Date:** 2026-06-13
**Status:** Approved
**Scope:** P1-4 — 浏览器端联机对战

## 1. 目标

让 GWT/WebGL 编译的浏览器客户端能够通过网络与桌面客户端对战。目前 `FyrenGwtLauncher` 仅支持本地双人 Demo。

## 2. 架构

```
                    ┌─ UDP :9876 ──── 桌面客户端（不变）
GameServer 进程 ────┤
                    ├─ WebSocket :9878 ─ 浏览器客户端（新增）
                    ├─ MatchManager ───── 共享匹配池（跨协议）
                    ├─ AuthHttpServer ──── 共享
                    └─ HttpStatusServer ── 共享
```

浏览器客户端通过 WebSocket 连接服务端，发送/接收相同的 Packet 二进制格式。桌面客户端继续走 UDP。两种客户端在同一个匹配池中对战。

## 3. 协议

WebSocket 传输所有现有 Packet 类型，二进制帧。既然 WebSocket 是可靠有序通道，不再区分可靠/不可靠——所有包直接发送。

Packet 序列化/反序列化（`Packet.deserialize()`）已兼容 GWT（纯字节操作，无 `java.net.*`）。

## 4. 新增文件

| 文件 | 职责 |
|------|------|
| `network/WsGameServer.java` | WebSocket 服务端，管理浏览器连接、包路由、跨协议转发 |
| `network/WsSession.java` | 单个浏览器 WebSocket 连接的状态封装 |
| `network/gwt/GwtWebSocket.java` | GWT JsInterop — 封装浏览器原生 WebSocket API |
| `network/gwt/GwtNetworkClient.java` | GWT 兼容的 GameClient — WebSocket 传输层 + 相同生命周期 |
| `sync/GwtFrameSyncManager.java` | GWT 兼容的帧同步引擎 — 主线程驱动，无多线程 |

## 5. 修改文件

| 文件 | 改动 |
|------|------|
| `GameServer.java` | 启动 WsGameServer（端口 9878），共享 MatchManager 引用 |
| `FyrenGwt.gwt.xml` | 添加 GWT 网络层 + GwtFrameSyncManager 源码路径 |
| `FyrenGwtLauncher.java` | 支持网络模式（WebSocket 连接服务器）和本地 Demo 模式 |
| `pom.xml` | 添加 `org.java-websocket:Java-WebSocket:1.5.6` |

## 6. 关键设计

### 6.1 GwtFrameSyncManager — 主线程驱动

当前 `FrameSyncManager` 用独立线程 + `Thread.sleep()` 驱动固定帧率。GWT 不支持多线程。替代方案：libGDX `render()` 回调驱动帧步进。逻辑完全相同（收集输入 → 预测 → 推进游戏世界 → 回滚检测），只是调度方式变了。

### 6.2 GwtNetworkClient — GameClient 镜像

`GameClient` 依赖 `java.net.DatagramSocket`、`java.net.http.HttpClient`、`ReentrantReadWriteLock`，这些 GWT 均不支持。`GwtNetworkClient` 复制其状态机和生命周期，传输层换为 WebSocket，移除线程安全代码。

状态机：`IDLE → CONNECTING → CONNECTED → MATCHING → MATCHED → PLAYING → GAME_OVER`

### 6.3 跨协议对战

WebSocket 浏览器客户端和 UDP 桌面客户端共享 MatchManager 匹配池。WsGameServer 负责将 WebSocket 收到的包路由到 MatchManager（匹配包）或转发给对手（输入包、结果包）。当对手是 UDP 客户端时，WsGameServer 将包通过 UdpServer 转发。

### 6.4 无 P2P（浏览器端）

浏览器无法做 UDP 打洞，浏览器客户端始终走服务器中继。桌面-桌面对局仍可使用现有 P2P 打洞。

## 7. 不做的

- 不做浏览器端 P2P（WebRTC 复杂度太高，后续考虑）
- 不做 WebSocket 的 ACK/重传（TCP 已提供可靠性）
- 不改现有桌面客户端代码
- 不改 GameWorld/Fighter/CollisionSystem 等游戏核心
