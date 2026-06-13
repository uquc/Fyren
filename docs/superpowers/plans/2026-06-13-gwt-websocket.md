# GWT WebSocket 网络对战 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 GWT/WebGL 浏览器客户端能通过网络与桌面客户端联机对战，共享匹配池。

**Architecture:** 服务端新增 WebSocket 监听(端口 9878)，与现有 UDP(9876) 共存。MatchManager 通过回调模式支持双传输层。GWT 客户端新增 WebSocket 传输层 + 主线程帧同步引擎，复用现有 Packet 序列化。

**Tech Stack:** org.java-websocket:Java-WebSocket:1.5.6, GWT JsInterop, libGDX GWT backend

---

### Task 1: Add org.java-websocket dependency

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add dependency**

在 `pom.xml` 的 `<dependencies>` 块末尾（`jbcrypt` dependency 之后、`</dependencies>` 之前）添加：

```xml
        <!-- WebSocket server (GWT browser client support) -->
        <dependency>
            <groupId>org.java-websocket</groupId>
            <artifactId>Java-WebSocket</artifactId>
            <version>1.5.6</version>
        </dependency>
```

- [ ] **Step 2: Verify**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: add org.java-websocket 1.5.6 for browser client support"
```

---

### Task 2: Create WsSession.java

**Files:**
- Create: `src/main/java/com/Fyren/network/WsSession.java`

- [ ] **Step 1: Create WsSession**

```java
package com.Fyren.network;

import org.java_websocket.WebSocket;

/**
 * 封装单个浏览器 WebSocket 连接的状态。
 * 对应 UdpServer.ClientSession，但不依赖 java.net。
 */
public class WsSession {
    public final WebSocket socket;
    public int playerId;
    public int rating;
    public int presetOrdinal = 1;
    public WsSession opponentSession;
    public int opponentId;
    public long lastHeartbeat;

    public WsSession(WebSocket socket) {
        this.socket = socket;
        this.lastHeartbeat = System.currentTimeMillis();
    }

    public boolean isInMatch() {
        return opponentSession != null;
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
mvn compile -q
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/Fyren/network/WsSession.java
git commit -m "feat: add WsSession — WebSocket connection state wrapper"
```

---

### Task 3: Create WsGameServer.java

**Files:**
- Create: `src/main/java/com/Fyren/network/WsGameServer.java`

- [ ] **Step 1: Create WsGameServer**

```java
package com.Fyren.network;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * WebSocket 游戏服务端 — 为 GWT/WebGL 浏览器客户端提供网络对战。
 *
 * 与 UdpServer 并列运行。浏览器客户端通过 WebSocket 二进制帧
 * 发送/接收 Packet（复用现有序列化格式）。
 */
public class WsGameServer extends WebSocketServer {

    private final ConcurrentHashMap<Integer, WsSession> clients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, GameSession> gameSessions = new ConcurrentHashMap<>();

    private BiConsumer<Packet, WsSession> onPacketReceived;
    private java.util.function.Consumer<Integer> onClientCountChanged;

    private static final long CLIENT_TIMEOUT_MS = 30_000;

    public WsGameServer(int port) {
        super(new InetSocketAddress(port));
        setConnectionLostTimeout(0);
    }

    // ===== WebSocket event handlers =====

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("[WsGameServer] 新连接: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        WsSession session = findSessionBySocket(conn);
        if (session != null) {
            System.out.println("[WsGameServer] 断开: playerId=" + session.playerId);
            if (session.opponentSession != null) {
                session.opponentSession.opponentSession = null;
                session.opponentSession.opponentId = -1;
            }
            clients.remove(session.playerId);
            gameSessions.entrySet().removeIf(e ->
                e.getValue().player1Id == session.playerId
                    || e.getValue().player2Id == session.playerId);
            notifyClientCountChanged();
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        // 只处理二进制
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        byte[] data = new byte[message.remaining()];
        message.get(data);

        Packet packet = Packet.deserialize(data);
        if (packet == null) return;

        WsSession session = findSessionBySocket(conn);

        switch (packet.type) {
            case MATCH_REQ:
                handleMatchRequest((MatchRequestPacket) packet, conn, session);
                break;
            case HEARTBEAT:
                handleHeartbeat(conn, session);
                break;
            case INPUT:
                handleInput((InputPacket) packet, session);
                break;
            default:
                if (onPacketReceived != null && session != null) {
                    onPacketReceived.accept(packet, session);
                }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("[WsGameServer] 错误: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        System.out.println("[WsGameServer] WebSocket 启动，端口: " + getPort());
    }

    // ===== Packet handlers =====

    private void handleMatchRequest(MatchRequestPacket packet, WebSocket conn, WsSession existingSession) {
        boolean isNew = !clients.containsKey(packet.playerId);
        WsSession session = clients.computeIfAbsent(packet.playerId, k -> {
            WsSession s = new WsSession(conn);
            s.playerId = packet.playerId;
            return s;
        });
        session.rating = packet.playerRating;
        session.presetOrdinal = packet.presetOrdinal;
        session.lastHeartbeat = System.currentTimeMillis();

        if (isNew) notifyClientCountChanged();

        System.out.println("[WsGameServer] 匹配请求: playerId=" + packet.playerId
                + ", rating=" + packet.playerRating);

        if (onPacketReceived != null) onPacketReceived.accept(packet, session);
    }

    private void handleHeartbeat(WebSocket conn, WsSession session) {
        if (session != null) {
            session.lastHeartbeat = System.currentTimeMillis();
        }
        HeartbeatPacket reply = new HeartbeatPacket(0);
        sendTo(reply, conn);
    }

    private void handleInput(InputPacket packet, WsSession session) {
        if (session == null || session.opponentSession == null) return;
        sendTo(packet, session.opponentSession.socket);
    }

    // ===== Send methods =====

    public void sendTo(Packet packet, WebSocket conn) {
        if (conn == null || !conn.isOpen()) return;
        try {
            conn.send(packet.serialize());
        } catch (Exception e) {
            System.err.println("[WsGameServer] 发送失败: " + e.getMessage());
        }
    }

    public void sendToPlayer(Packet packet, int playerId) {
        WsSession session = clients.get(playerId);
        if (session != null) sendTo(packet, session.socket);
    }

    // ===== Session management =====

    /** 建立游戏会话（匹配成功后调用） */
    public void createGameSession(int player1Id, int player2Id) {
        WsSession p1 = clients.get(player1Id);
        WsSession p2 = clients.get(player2Id);
        if (p1 == null || p2 == null) return;

        p1.opponentSession = p2;
        p1.opponentId = player2Id;
        p2.opponentSession = p1;
        p2.opponentId = player1Id;

        String key = Math.min(player1Id, player2Id) + "_" + Math.max(player1Id, player2Id);
        gameSessions.put(key, new GameSession(key, player1Id, player2Id));
        System.out.println("[WsGameServer] 会话建立: " + key);
    }

    /** 获取玩家地址字符串（跨协议兼容，用于 MatchResponsePacket） */
    public String getPlayerAddress(int playerId) {
        WsSession s = clients.get(playerId);
        if (s != null && s.socket.getRemoteSocketAddress() != null) {
            return s.socket.getRemoteSocketAddress().getAddress().getHostAddress();
        }
        return "";
    }

    public void checkClientTimeouts() {
        long now = System.currentTimeMillis();
        List<Integer> timeoutIds = new ArrayList<>();
        for (Map.Entry<Integer, WsSession> entry : clients.entrySet()) {
            if (now - entry.getValue().lastHeartbeat > CLIENT_TIMEOUT_MS) {
                timeoutIds.add(entry.getKey());
            }
        }
        for (int id : timeoutIds) {
            WsSession s = clients.remove(id);
            if (s != null && s.socket.isOpen()) s.socket.close();
            gameSessions.entrySet().removeIf(e ->
                e.getValue().player1Id == id || e.getValue().player2Id == id);
        }
        if (!timeoutIds.isEmpty()) notifyClientCountChanged();
    }

    private WsSession findSessionBySocket(WebSocket conn) {
        for (WsSession s : clients.values()) {
            if (s.socket == conn) return s;
        }
        return null;
    }

    // ===== Getters/Setters =====

    public void setOnPacketReceived(BiConsumer<Packet, WsSession> cb) { this.onPacketReceived = cb; }
    public void setOnClientCountChanged(java.util.function.Consumer<Integer> cb) { this.onClientCountChanged = cb; }
    public ConcurrentHashMap<Integer, WsSession> getClients() { return clients; }

    private void notifyClientCountChanged() {
        if (onClientCountChanged != null) onClientCountChanged.accept(clients.size());
    }

    // ===== Inner class =====

    public static class GameSession {
        public final String sessionKey;
        public final int player1Id;
        public final int player2Id;
        public final long startTime;
        GameSession(String key, int p1, int p2) {
            this.sessionKey = key; this.player1Id = p1; this.player2Id = p2;
            this.startTime = System.currentTimeMillis();
        }
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/Fyren/network/WsGameServer.java
git commit -m "feat: add WsGameServer — WebSocket server for browser clients"
```

---

### Task 4: Refactor MatchManager — add transport-agnostic callbacks

**Files:**
- Modify: `src/main/java/com/Fyren/match/MatchManager.java`

**Goal:** 让 MatchManager 不直接调用 UdpServer 的方法，而是通过可替换的回调发送匹配响应。同时添加 "匹配双方ID" 回调让 GameServer 能跨协议建立会话。

- [ ] **Step 1: Add new fields to MatchManager**

在 `MatchManager.java` 的 `private Runnable onMatchEnded;` 之后添加：

```java
    // 匹配响应发送器 — 由 GameServer 注入，支持多传输层
    private MatchResponseSender matchResponseSender;
    // 匹配双方回调 — GameServer 用于跨协议建立会话
    private java.util.function.BiConsumer<Integer, Integer> onMatchFoundCallback;

    @FunctionalInterface
    public interface MatchResponseSender {
        void sendMatchResponse(int playerId, MatchResponsePacket response,
                               String opponentAddress, int opponentPort);
    }

    public void setMatchResponseSender(MatchResponseSender sender) { this.matchResponseSender = sender; }
    public void setOnMatchFoundCallback(java.util.function.BiConsumer<Integer, Integer> cb) { this.onMatchFoundCallback = cb; }
```

- [ ] **Step 2: Rewrite handleMatchFound to use callbacks**

用以下内容替换 `handleMatchFound` 方法末尾（从 `// 获取双方角色预设` 到方法结束的 `}`）：

```java
        // 获取双方角色预设
        int p1Preset = playerPresets.getOrDefault(p1.playerId, 1);
        int p2Preset = playerPresets.getOrDefault(p2.playerId, 1);

        String addr1 = session1.address != null ? session1.address.getHostString() : "";
        int port1 = session1.address != null ? session1.address.getPort() : 0;
        String addr2 = session2.address != null ? session2.address.getHostString() : "";
        int port2 = session2.address != null ? session2.address.getPort() : 0;

        MatchResponsePacket resp1 = new MatchResponsePacket(
                nextSequence(), MatchResponsePacket.STATUS_MATCHED,
                p2.playerId, p2.rating, addr2, port2, p2Preset);
        MatchResponsePacket resp2 = new MatchResponsePacket(
                nextSequence(), MatchResponsePacket.STATUS_MATCHED,
                p1.playerId, p1.rating, addr1, port1, p1Preset);

        if (matchResponseSender != null) {
            matchResponseSender.sendMatchResponse(p1.playerId, resp1, addr2, port2);
            matchResponseSender.sendMatchResponse(p2.playerId, resp2, addr1, port1);
        } else {
            // 向后兼容：直接用 UDP
            server.sendReliableTo(resp1, session1.address);
            server.sendReliableTo(resp2, session2.address);
        }

        System.out.printf("[MatchManager] 已通知双方匹配结果: player%d ↔ player%d\n",
                p1.playerId, p2.playerId);

        // 通知匹配创建
        if (onMatchCreated != null) onMatchCreated.run();
        // 通知匹配双方ID（GameServer 用于跨协议会话建立）
        if (onMatchFoundCallback != null) onMatchFoundCallback.accept(p1.playerId, p2.playerId);
```

- [ ] **Step 3: Verify compile + tests**

```bash
mvn compile -q && mvn test -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/Fyren/match/MatchManager.java
git commit -m "refactor: MatchManager — transport-agnostic match response + match-found callback"
```

---

### Task 5: Integrate WsGameServer into GameServer

**Files:**
- Modify: `src/main/java/com/Fyren/GameServer.java`

- [ ] **Step 1: Add fields**

在 `private AuthHttpServer authHttpServer;` 之后添加：

```java
    private WsGameServer wsGameServer;
    private final Set<Integer> wsClientIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private java.util.concurrent.ScheduledExecutorService maintenanceScheduler;
```

在 import 区添加：
```java
import com.Fyren.network.WsGameServer;
```

- [ ] **Step 2: Add matchResponseSender setup**

在 `matchManager.start();` 之前添加：

```java
        // 传输层无关的匹配响应发送器（UDP + WebSocket 统一入口）
        matchManager.setMatchResponseSender((playerId, response, opponentAddr, opponentPort) -> {
            // 优先 UDP
            UdpServer.ClientSession udpSession = udpServer.getClients().get(playerId);
            if (udpSession != null && udpSession.address != null) {
                udpServer.sendReliableTo(response, udpSession.address);
                return;
            }
            // WebSocket fallback
            if (wsGameServer != null) {
                wsGameServer.sendToPlayer(response, playerId);
            }
        });
```

- [ ] **Step 3: Add onMatchFoundCallback — cross-protocol session setup**

在 Step 2 的代码之后继续添加：

```java
        // 匹配成功后跨协议建立游戏会话
        matchManager.setOnMatchFoundCallback((p1, p2) -> {
            if (wsGameServer != null) {
                boolean p1ws = wsClientIds.contains(p1);
                boolean p2ws = wsClientIds.contains(p2);
                if (p1ws || p2ws) {
                    wsGameServer.createGameSession(p1, p2);
                }
            }
        });
```

- [ ] **Step 4: Add WsGameServer startup and packet callback**

在 `matchManager.start();` 之后、authHttpServer 启动之前，添加：

```java
        // 启动 WebSocket 服务端（浏览器客户端，端口 9878）
        wsGameServer = new WsGameServer(9878);
        wsGameServer.setOnPacketReceived((packet, wsSession) -> {
            if (packet instanceof MatchRequestPacket) {
                MatchRequestPacket mrp = (MatchRequestPacket) packet;
                // 创建或复用 UdpServer.ClientSession（MatchManager 需要）
                UdpServer.ClientSession cs = udpServer.getClients().computeIfAbsent(
                    mrp.playerId, UdpServer.ClientSession::new);
                cs.rating = mrp.playerRating;
                cs.lastHeartbeat = System.currentTimeMillis();
                wsClientIds.add(mrp.playerId);
                matchManager.handleMatchRequest(mrp, cs);
            } else if (packet instanceof ResultPacket) {
                ResultPacket rp = (ResultPacket) packet;
                String matchKey = Math.min(rp.player1Id, rp.player2Id) + "-"
                    + Math.max(rp.player1Id, rp.player2Id);
                if (!reportedMatches.add(matchKey)) return;
                System.out.printf("[GameServer] 比赛结果(WS): P%d vs P%d, 胜者=%d\n",
                        rp.player1Id, rp.player2Id, rp.winnerId);
                reportMatch(rp.player1Id, rp.player2Id, rp.winnerId);
                if (redisService != null && redisService.isAvailable()) {
                    try {
                        com.Fyren.match.PlayerRating r1 = matchManager.getPlayerRating(rp.player1Id);
                        com.Fyren.match.PlayerRating r2 = matchManager.getPlayerRating(rp.player2Id);
                        if (r1 != null) redisService.updateMmr(rp.player1Id, r1.getRating());
                        if (r2 != null) redisService.updateMmr(rp.player2Id, r2.getRating());
                    } catch (Exception ex) { /* non-critical */ }
                }
            }
        });
        wsGameServer.start();
```

- [ ] **Step 5: Add maintenance scheduler + timeout check**

在 `udpServer.start();` 之后添加：

```java
        maintenanceScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
```

在 `wsGameServer.start();` 之后添加：

```java
        maintenanceScheduler.scheduleAtFixedRate(() -> {
            if (wsGameServer != null) wsGameServer.checkClientTimeouts();
        }, 5, 5, java.util.concurrent.TimeUnit.SECONDS);
```

- [ ] **Step 6: Add cleanup in stop()**

在 `stop()` 方法中，`if (redisService != null) redisService.close();` 之前添加：

```java
        if (wsGameServer != null) {
            try { wsGameServer.stop(); } catch (Exception e) { /* ignore */ }
        }
        if (maintenanceScheduler != null) maintenanceScheduler.shutdown();
```

更新控制台输出（在 `start()` 末尾的打印块中添加 WebSocket 端口）：

```java
        System.out.println("  WebSocket 端口: 9878");
```

- [ ] **Step 7: Verify compile + tests**

```bash
mvn compile -q && mvn test -q
```
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/Fyren/GameServer.java
git commit -m "feat: integrate WsGameServer into GameServer — shared match pool, cross-protocol"
```

---

### Task 6: Create GwtWebSocket.java (GWT JsInterop)

**Files:**
- Create: `src/main/java/com/Fyren/network/gwt/GwtWebSocket.java`

- [ ] **Step 1: Create GwtWebSocket — JSNI wrapper for browser WebSocket**

```java
package com.Fyren.network.gwt;

import com.google.gwt.core.client.JavaScriptObject;

/**
 * GWT JSNI 封装浏览器原生 WebSocket API。
 * 用于 GWT/WebGL 客户端与服务端的 WebSocket 通信。
 */
public class GwtWebSocket {

    public interface Callback {
        void onOpen();
        void onMessage(byte[] data);
        void onClose(int code, String reason);
        void onError(String message);
    }

    private JavaScriptObject ws;
    private final Callback callback;
    private final String url;

    public GwtWebSocket(String url, Callback callback) {
        this.url = url;
        this.callback = callback;
    }

    /** 打开连接。必须在 GWT 客户端代码中调用。 */
    public native void connect() /*-{
        var self = this;
        var ws = new WebSocket(this.@com.Fyren.network.gwt.GwtWebSocket::url);
        ws.binaryType = "arraybuffer";

        ws.onopen = function() {
            self.@com.Fyren.network.gwt.GwtWebSocket::onOpen()();
        };

        ws.onmessage = function(event) {
            var data;
            if (event.data instanceof ArrayBuffer) {
                data = new Int8Array(event.data);
            } else {
                // Blob fallback — read via FileReader (sync not possible, skip for now)
                return;
            }
            self.@com.Fyren.network.gwt.GwtWebSocket::onMessage([B)(data);
        };

        ws.onclose = function(event) {
            self.@com.Fyren.network.gwt.GwtWebSocket::onClose(ILjava/lang/String;)(event.code || 0, event.reason || "");
        };

        ws.onerror = function() {
            self.@com.Fyren.network.gwt.GwtWebSocket::onError(Ljava/lang/String;)("WebSocket error");
        };

        this.@com.Fyren.network.gwt.GwtWebSocket::ws = ws;
    }-*/;

    /** 发送二进制数据 */
    public native void send(byte[] data) /*-{
        var ws = this.@com.Fyren.network.gwt.GwtWebSocket::ws;
        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(new Uint8Array(data).buffer);
        }
    }-*/;

    /** 关闭连接 */
    public native void close() /*-{
        var ws = this.@com.Fyren.network.gwt.GwtWebSocket::ws;
        if (ws) {
            ws.onclose = null;
            ws.close();
        }
    }-*/;

    /** 获取就绪状态 (0=CONNECTING, 1=OPEN, 2=CLOSING, 3=CLOSED) */
    public native int getReadyState() /*-{
        var ws = this.@com.Fyren.network.gwt.GwtWebSocket::ws;
        return ws ? ws.readyState : 3;
    }-*/;

    // JSNI 回调桥接方法
    private void onOpen() {
        callback.onOpen();
    }

    private void onMessage(byte[] data) {
        // JSNI 传过来的 Int8Array 直接是 byte[]
        if (data == null || data.length == 0) return;
        callback.onMessage(data);
    }

    private void onClose(int code, String reason) {
        callback.onClose(code, reason);
    }

    private void onError(String message) {
        callback.onError(message);
    }

    public boolean isOpen() {
        return getReadyState() == 1;
    }
}
```

> **注意：** JSNI 方法中的 `Int8Array` 转换——GWT JSNI 中 `[B` 类型参数接收 JavaScript `Int8Array`。但浏览器 WebSocket 的 `onmessage` 事件中 `event.data` 是 `ArrayBuffer`。需要在 JSNI 中做 `new Int8Array(event.data)` 转换。

- [ ] **Step 2: Verify GWT compile (only syntax check for now)**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS. (GWT JSNI 语法在 javac 中不检查，只在 GWT 编译时验证——Task 11 做。)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/Fyren/network/gwt/GwtWebSocket.java
git commit -m "feat: add GwtWebSocket — JSNI browser WebSocket wrapper"
```

---

### Task 7: Create GwtFrameSyncManager.java

**Files:**
- Create: `src/main/java/com/Fyren/sync/GwtFrameSyncManager.java`

- [ ] **Step 1: Create GwtFrameSyncManager — main-thread-driven frame sync**

```java
package com.Fyren.sync;

import com.Fyren.game.GameWorld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GWT 兼容的帧同步管理器 — 无 java.util.concurrent，主线程驱动。
 *
 * 算法与 FrameSyncManager 完全相同（锁步 + 回滚），但：
 * - 不由独立线程驱动，改为外部调用 tick()（从 render() 驱动）
 * - 使用 HashMap 替代 ConcurrentHashMap（GWT 单线程）
 * - 无 Thread.sleep / ScheduledExecutor
 * - 无 ReentrantReadWriteLock（单线程不需要锁）
 */
public class GwtFrameSyncManager {

    private static final int TARGET_FPS = 60;
    private static final int FRAME_TIME_MS = 1000 / TARGET_FPS;
    private static final int ROLLBACK_MAX_FRAMES = 10;

    @FunctionalInterface
    public interface LocalInputProvider {
        InputCommand getInput(int frameNumber, int localPlayerId);
    }

    private final GameWorld gameWorld;
    private final InputBuffer localInputBuffer;
    private final Map<Integer, InputBuffer> remoteInputBuffers = new HashMap<>();

    private boolean running = false;
    private int localPlayerId = 1;
    private LocalInputProvider localInputProvider;
    private Runnable onGameOver;

    // 帧时间管理（用 delta 累积替代固定时钟）
    private float accumulatedMs = 0f;
    private int currentFrame = 0;

    // 预测相关
    private final Map<Integer, InputCommand> lastKnownInputs = new HashMap<>();
    private int confirmedFrame = 0;

    public GwtFrameSyncManager(GameWorld gameWorld) {
        this.gameWorld = gameWorld;
        this.localInputBuffer = new InputBuffer(120);
    }

    public void setLocalInputProvider(LocalInputProvider provider) { this.localInputProvider = provider; }
    public void setLocalPlayerId(int id) { this.localPlayerId = id; }
    public void setOnGameOver(Runnable cb) { this.onGameOver = cb; }
    public int getLocalPlayerId() { return localPlayerId; }

    public void start() {
        running = true;
        currentFrame = 0;
        accumulatedMs = 0f;
    }

    public void stop() {
        running = false;
    }

    public boolean isRunning() { return running; }

    /**
     * 主驱动方法 — 每帧从 render() 调用。
     * @param deltaMs 自上次渲染以来经过的时间（毫秒），通常 ~16.6ms
     */
    public void tick(float deltaMs) {
        if (!running) return;

        accumulatedMs += deltaMs;

        // 以固定时间步长推进（追赶积压帧，最多 5 帧防止螺旋）
        int maxFrames = 5;
        while (accumulatedMs >= FRAME_TIME_MS && maxFrames > 0) {
            accumulatedMs -= FRAME_TIME_MS;
            maxFrames--;
            tickOneFrame();
        }

        // 防止长时间暂停导致的帧雪崩
        if (accumulatedMs > 200f) accumulatedMs = 0f;
    }

    private void tickOneFrame() {
        // 1. 收集本地输入
        InputCommand localCmd = collectLocalInput(currentFrame);
        localInputBuffer.addInput(localCmd);

        // 2. 获取所有输入并预测
        List<InputCommand> allInputs = gatherInputs(currentFrame);
        List<InputCommand> predicted = predictInputs(allInputs, currentFrame);

        // 4. 推进游戏逻辑
        gameWorld.update(predicted, currentFrame);

        // 5. 检测游戏结束
        if (gameWorld.isGameOver()) {
            running = false;
            if (onGameOver != null) onGameOver.run();
            return;
        }

        // 6. 检查回滚
        checkAndRollback(currentFrame);

        currentFrame++;
    }

    private InputCommand collectLocalInput(int frameNumber) {
        if (localInputProvider != null) {
            return localInputProvider.getInput(frameNumber, localPlayerId);
        }
        return new InputCommand(frameNumber, localPlayerId);
    }

    private List<InputCommand> gatherInputs(int frameNumber) {
        List<InputCommand> inputs = new ArrayList<>();
        inputs.add(localInputBuffer.getInput(frameNumber));
        for (InputBuffer remoteBuf : remoteInputBuffers.values()) {
            inputs.add(remoteBuf.getInput(frameNumber));
        }
        return inputs;
    }

    private List<InputCommand> predictInputs(List<InputCommand> inputs, int frameNumber) {
        List<InputCommand> result = new ArrayList<>();
        java.util.Set<Integer> seenPlayers = new java.util.HashSet<>();

        for (InputCommand cmd : inputs) {
            if (cmd != null) {
                result.add(cmd);
                seenPlayers.add(cmd.playerId);
                if (!cmd.isEmpty()) {
                    lastKnownInputs.put(cmd.playerId, cmd);
                }
            }
        }

        // 预测缺失的输入
        for (Map.Entry<Integer, InputBuffer> entry : remoteInputBuffers.entrySet()) {
            int remoteId = entry.getKey();
            if (!seenPlayers.contains(remoteId)) {
                InputCommand predicted = lastKnownInputs.get(remoteId);
                if (predicted != null) {
                    result.add(copyInput(predicted, frameNumber, remoteId));
                }
            }
        }
        if (!seenPlayers.contains(localPlayerId)) {
            InputCommand predicted = lastKnownInputs.get(localPlayerId);
            if (predicted != null) {
                result.add(copyInput(predicted, frameNumber, localPlayerId));
            }
        }

        return result;
    }

    private InputCommand copyInput(InputCommand src, int frame, int playerId) {
        InputCommand copy = new InputCommand(frame, playerId);
        copy.up = src.up; copy.down = src.down;
        copy.left = src.left; copy.right = src.right;
        copy.punch = src.punch; copy.kick = src.kick;
        copy.special = src.special;
        return copy;
    }

    private void checkAndRollback(int currentFrame) {
        for (Map.Entry<Integer, InputBuffer> entry : remoteInputBuffers.entrySet()) {
            int remoteId = entry.getKey();
            InputBuffer remoteBuf = entry.getValue();

            while (confirmedFrame < remoteBuf.getCurrentFrame()) {
                int checkFrame = confirmedFrame + 1;
                InputCommand confirmedInput = remoteBuf.getInput(checkFrame);
                if (confirmedInput == null) break;

                InputCommand predictedInput = lastKnownInputs.get(remoteId);
                if (predictedInput != null && !predictedInput.equals(confirmedInput)) {
                    int rollbackFrames = Math.min(currentFrame - confirmedFrame, ROLLBACK_MAX_FRAMES);
                    rollback(currentFrame - rollbackFrames);
                    return;
                }
                confirmedFrame++;
            }
        }
    }

    private void rollback(int targetFrame) {
        System.out.println("[GwtFSM] 回滚到帧: " + targetFrame);
        int originalFrame = currentFrame;

        // 使用 GameWorld.rollbackTo() 回滚状态，然后从目标帧重放
        gameWorld.rollbackTo(targetFrame);
        for (int frame = targetFrame; frame < originalFrame; frame++) {
            List<InputCommand> inputs = gatherInputs(frame);
            List<InputCommand> predicted = predictInputs(inputs, frame);
            gameWorld.update(predicted, frame);
        }
    }

    /** 接收远程输入（由网络层在收到 INPUT 包时调用） */
    public void receiveRemoteInput(InputCommand remoteCmd) {
        int playerId = remoteCmd.playerId;
        remoteInputBuffers.computeIfAbsent(playerId, k -> new InputBuffer(120))
                .addInput(remoteCmd);
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/Fyren/sync/GwtFrameSyncManager.java
git commit -m "feat: add GwtFrameSyncManager — main-thread-driven frame sync for GWT"
```

---

### Task 8: Create GwtNetworkClient.java

**Files:**
- Create: `src/main/java/com/Fyren/network/gwt/GwtNetworkClient.java`

- [ ] **Step 1: Create GwtNetworkClient**

```java
package com.Fyren.network.gwt;

import com.Fyren.game.FighterPreset;
import com.Fyren.game.GameWorld;
import com.Fyren.match.PlayerRating;
import com.Fyren.network.*;
import com.Fyren.sync.GwtFrameSyncManager;
import com.Fyren.sync.InputCommand;
import com.Fyren.util.InputCodec;

/**
 * GWT 兼容的网络客户端 — WebSocket 传输层 + GameClient 生命周期。
 *
 * 与 GameClient 镜像：相同状态机，但传输层为 WebSocket 而非 UDP。
 * 无 java.net.*、无多线程、无 P2P。
 */
public class GwtNetworkClient {

    public enum ClientState {
        IDLE, CONNECTING, CONNECTED, MATCHING, MATCHED, PLAYING, GAME_OVER, DISCONNECTED
    }

    public interface GameEventCallback {
        void onStateChanged(ClientState newState);
        void onMatchFound(int opponentId, int opponentRating);
        void onGameStart();
        void onGameOver(int winnerId);
        void onError(String message);
    }

    private final String serverHost;
    private final int serverWsPort;
    private final int localPlayerId;
    private FighterPreset preset;
    private final PlayerRating playerRating;

    private GwtWebSocket webSocket;
    private GameWorld gameWorld;
    private GwtFrameSyncManager frameSyncManager;

    private volatile ClientState state = ClientState.IDLE;
    private int opponentId = -1;
    private int opponentRating = 1000;
    private int opponentPresetOrdinal = 1;
    private boolean opponentReady = false;

    private int sequenceCounter = 0;
    private InputCommand currentLocalInput = null;
    private GameEventCallback callback;

    public GwtNetworkClient(String serverHost, int serverWsPort, int localPlayerId, FighterPreset preset) {
        this.serverHost = serverHost;
        this.serverWsPort = serverWsPort;
        this.localPlayerId = localPlayerId;
        this.preset = preset;
        this.playerRating = new PlayerRating(localPlayerId);
        this.gameWorld = new GameWorld();
    }

    public GwtNetworkClient(String serverHost, int serverWsPort, int localPlayerId,
                            int initialRating, FighterPreset preset) {
        this.serverHost = serverHost;
        this.serverWsPort = serverWsPort;
        this.localPlayerId = localPlayerId;
        this.preset = preset;
        this.playerRating = new PlayerRating(localPlayerId, initialRating);
        this.gameWorld = new GameWorld();
    }

    // ========== Lifecycle ==========

    public void connect() {
        setState(ClientState.CONNECTING);

        String wsUrl = "ws://" + serverHost + ":" + serverWsPort;
        webSocket = new GwtWebSocket(wsUrl, new GwtWebSocket.Callback() {
            @Override
            public void onOpen() {
                setState(ClientState.CONNECTED);
                System.out.println("[GwtClient] WebSocket 已连接");
            }

            @Override
            public void onMessage(byte[] data) {
                handlePacket(data);
            }

            @Override
            public void onClose(int code, String reason) {
                setState(ClientState.DISCONNECTED);
                System.out.println("[GwtClient] 断开: " + code + " " + reason);
            }

            @Override
            public void onError(String message) {
                if (callback != null) callback.onError(message);
            }
        });
        webSocket.connect();
    }

    public void requestMatch() {
        if (state != ClientState.CONNECTED) return;
        setState(ClientState.MATCHING);

        MatchRequestPacket req = new MatchRequestPacket(
                nextSequence(), localPlayerId, playerRating.getRating(), preset.ordinal());
        sendPacket(req);
        System.out.println("[GwtClient] 匹配请求已发送 (rating=" + playerRating.getRating() + ")");
    }

    public void cancelMatch() {
        if (state != ClientState.MATCHING) return;
        MatchRequestPacket cancel = new MatchRequestPacket(nextSequence(), localPlayerId, -1);
        sendPacket(cancel);
        setState(ClientState.CONNECTED);
    }

    public void startGame() {
        if (state != ClientState.MATCHED) return;
        setState(ClientState.PLAYING);

        FighterPreset oppPreset = FighterPreset.values()[opponentPresetOrdinal];
        gameWorld.setupPlayers(preset, oppPreset);

        frameSyncManager = new GwtFrameSyncManager(gameWorld);
        frameSyncManager.setLocalPlayerId(localPlayerId);
        frameSyncManager.setLocalInputProvider((frameNumber, playerId) -> {
            InputCommand cmd = currentLocalInput;
            if (cmd == null) return new InputCommand(frameNumber, playerId);
            cmd.frameNumber = frameNumber;
            return cmd;
        });
        frameSyncManager.setOnGameOver(() -> {
            int worldWinnerId = gameWorld.getWinnerId();
            int actualWinnerId;
            if (worldWinnerId == 0) actualWinnerId = -1;
            else if (worldWinnerId == 1) actualWinnerId = localPlayerId;
            else actualWinnerId = opponentId;
            reportResult(actualWinnerId);
        });
        frameSyncManager.start();

        if (callback != null) callback.onGameStart();
        System.out.println("[GwtClient] 游戏开始! 对手=" + opponentId);
    }

    public void disconnect() {
        if (frameSyncManager != null) frameSyncManager.stop();
        if (webSocket != null) webSocket.close();
        setState(ClientState.DISCONNECTED);
    }

    public void resetToIdle() {
        this.opponentId = -1;
        this.opponentRating = 1000;
        this.opponentPresetOrdinal = 1;
        this.opponentReady = false;
        this.frameSyncManager = null;
        this.sequenceCounter = 0;
        this.currentLocalInput = null;
        setState(ClientState.CONNECTED);
    }

    // ========== Input ==========

    public void setCurrentLocalInput(InputCommand cmd) {
        this.currentLocalInput = cmd;
    }

    public void submitInput(boolean up, boolean down, boolean left, boolean right,
                            boolean punch, boolean kick, boolean special) {
        if (state != ClientState.PLAYING) return;
        InputCommand cmd = new InputCommand(0, localPlayerId);
        cmd.up = up; cmd.down = down; cmd.left = left; cmd.right = right;
        cmd.punch = punch; cmd.kick = kick; cmd.special = special;
        this.currentLocalInput = cmd;
        sendInputToOpponent(cmd);
    }

    private void sendInputToOpponent(InputCommand cmd) {
        if (cmd == null || cmd.isEmpty()) return;
        InputPacket packet = InputCodec.encode(cmd, nextSequence());
        sendPacket(packet);
    }

    // ========== Network ==========

    private void sendPacket(Packet packet) {
        if (webSocket != null && webSocket.isOpen()) {
            webSocket.send(packet.serialize());
        }
    }

    private void handlePacket(byte[] data) {
        Packet packet = Packet.deserialize(data);
        if (packet == null) return;

        switch (packet.type) {
            case INPUT:
                handleInputPacket((InputPacket) packet);
                break;
            case MATCH_RES:
                handleMatchResponse((MatchResponsePacket) packet);
                break;
            case HEARTBEAT:
                // 回复心跳
                sendPacket(new HeartbeatPacket(nextSequence()));
                break;
        }
    }

    private void handleInputPacket(InputPacket packet) {
        if (frameSyncManager == null) return;
        InputCommand cmd = InputCodec.decode(packet);
        if (cmd.playerId == localPlayerId) return;
        frameSyncManager.receiveRemoteInput(cmd);
    }

    private void handleMatchResponse(MatchResponsePacket packet) {
        switch (packet.matchStatus) {
            case MatchResponsePacket.STATUS_WAITING:
                System.out.println("[GwtClient] 匹配等待中...");
                break;

            case MatchResponsePacket.STATUS_MATCHED:
                if (state == ClientState.PLAYING || state == ClientState.GAME_OVER) return;
                this.opponentId = packet.opponentId;
                this.opponentRating = packet.opponentRating;
                this.opponentPresetOrdinal = packet.opponentPresetOrdinal;
                this.opponentReady = true;
                setState(ClientState.MATCHED);

                System.out.printf("[GwtClient] 匹配成功! 对手: player%d (rating=%d) preset=%s\n",
                        packet.opponentId, packet.opponentRating,
                        FighterPreset.values()[packet.opponentPresetOrdinal].getDisplayName());

                if (callback != null) {
                    callback.onMatchFound(packet.opponentId, packet.opponentRating);
                }
                break;

            case MatchResponsePacket.STATUS_CANCELLED:
                System.out.println("[GwtClient] 匹配已取消");
                setState(ClientState.CONNECTED);
                break;

            case MatchResponsePacket.STATUS_ERROR:
                System.err.println("[GwtClient] 匹配出错");
                setState(ClientState.CONNECTED);
                break;
        }
    }

    private void reportResult(int winnerId) {
        ResultPacket result = new ResultPacket(nextSequence(), localPlayerId, opponentId, winnerId);
        sendPacket(result);
        setState(ClientState.GAME_OVER);
        if (callback != null) callback.onGameOver(winnerId);
        System.out.printf("[GwtClient] 比赛结果已上报: winner=%d\n", winnerId);
    }

    // ========== Utils ==========

    private void setState(ClientState newState) {
        ClientState old = this.state;
        this.state = newState;
        if (old != newState && callback != null) {
            callback.onStateChanged(newState);
        }
    }

    private synchronized int nextSequence() {
        return ++sequenceCounter;
    }

    // ========== Getters/Setters ==========

    public ClientState getState() { return state; }
    public int getLocalPlayerId() { return localPlayerId; }
    public int getOpponentId() { return opponentId; }
    public int getOpponentRating() { return opponentRating; }
    public int getOpponentPresetOrdinal() { return opponentPresetOrdinal; }
    public FighterPreset getPreset() { return preset; }
    public void setPreset(FighterPreset p) { this.preset = p; }
    public PlayerRating getPlayerRating() { return playerRating; }
    public GameWorld getGameWorld() { return gameWorld; }
    public GwtFrameSyncManager getFrameSyncManager() { return frameSyncManager; }
    public void setCallback(GameEventCallback cb) { this.callback = cb; }
}
```

- [ ] **Step 2: Verify compile**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/Fyren/network/gwt/GwtNetworkClient.java
git commit -m "feat: add GwtNetworkClient — WebSocket network client for GWT"
```

---

### Task 9: Update FyrenGwt.gwt.xml — add source paths

**Files:**
- Modify: `src/main/java/com/Fyren/FyrenGwt.gwt.xml`

- [ ] **Step 1: Add network GWT source and sync sources**

在 `FyrenGwt.gwt.xml` 中，在现有的 `<source>` 块之后（`game` source 之后），添加：

```xml
    <!-- GWT WebSocket 网络层 -->
    <source path="network/gwt">
        <include name="*.java"/>
    </source>

    <!-- 网络包（Packet 序列化 — 纯字节操作，GWT 兼容） -->
    <source path="network">
        <include name="Packet.java"/>
        <include name="InputPacket.java"/>
        <include name="StatePacket.java"/>
        <include name="HeartbeatPacket.java"/>
        <include name="MatchRequestPacket.java"/>
        <include name="MatchResponsePacket.java"/>
        <include name="AckPacket.java"/>
        <include name="ResultPacket.java"/>
        <include name="P2pPacket.java"/>
    </source>

    <!-- GWT 帧同步管理器 -->
    <source path="sync">
        <include name="GwtFrameSyncManager.java"/>
    </source>

    <!-- 输入编解码（纯字节操作） -->
    <source path="util">
        <include name="InputCodec.java"/>
    </source>

    <!-- MMR 评分（纯数学计算） -->
    <source path="match">
        <include name="PlayerRating.java"/>
    </source>
```

- [ ] **Step 2: Verify GWT module XML is well-formed**

```bash
xmllint --noout src/main/java/com/Fyren/FyrenGwt.gwt.xml 2>&1 || echo "xmllint not available, skip"
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/Fyren/FyrenGwt.gwt.xml
git commit -m "feat: add GWT source paths for WebSocket networking"
```

---

### Task 10: Update FyrenGwtLauncher — support network mode

**Files:**
- Modify: `src/main/java/com/Fyren/render/libgdx/gwt/FyrenGwtLauncher.java`

- [ ] **Step 1: Add network mode support to FyrenGwtLauncher**

修改 `FyrenGwtLauncher`，在保留现有 Demo 模式的同时，添加网络模式支持。

关键改动：
1. 添加 `GwtNetworkClient` 字段
2. 在 `create()` 中检测 URL 参数 `?mode=network&server=...&playerId=...`
3. 网络模式下使用 `GwtNetworkClient` + `GwtFrameSyncManager`
4. Demo 模式保持现有逻辑不变

```java
// ---- 新增字段 ----
private GwtNetworkClient networkClient;
private boolean networkMode = false;

// ---- create() 修改 ----
@Override
public void create() {
    // 检测 URL 参数决定模式
    String mode = getUrlParam("mode");
    networkMode = "network".equals(mode);

    if (networkMode) {
        createNetworkMode();
    } else {
        createDemoMode();
    }
}

private void createDemoMode() {
    // ... 现有 create() 的全部代码 ...
    Gdx.graphics.setTitle("Fyren WebGL — " + p1Preset.getDisplayName() + " vs " + p2Preset.getDisplayName());
    gameWorld = new GameWorld();
    gameWorld.setupPlayers(p1Preset, p2Preset);
    // ...
}

private void createNetworkMode() {
    String server = getUrlParam("server");
    String playerIdStr = getUrlParam("playerId");
    if (server == null || server.isEmpty()) server = "localhost";
    int wsPort = 9878;
    int playerId = 1;
    try { if (playerIdStr != null) playerId = Integer.parseInt(playerIdStr); } catch (NumberFormatException e) {}

    Gdx.graphics.setTitle("Fyren WebGL — Online (P" + playerId + ")");

    networkClient = new GwtNetworkClient(server, wsPort, playerId, FighterPreset.KAGE);
    networkClient.setCallback(new GwtNetworkClient.GameEventCallback() {
        @Override public void onStateChanged(GwtNetworkClient.ClientState s) {}
        @Override public void onMatchFound(int oppId, int oppRating) {
            networkClient.startGame();
        }
        @Override public void onGameStart() {}
        @Override public void onGameOver(int winnerId) {}
        @Override public void onError(String msg) {
            System.err.println("[GwtClient] " + msg);
        }
    });
    networkClient.connect();
    networkClient.requestMatch();

    // 初始化渲染组件
    gameWorld = networkClient.getGameWorld();
    inputHandler = new GdxInputHandler();
    cameraController = new CameraController(960, 540);
    spriteRenderer = new SpriteRenderer();
    hudRenderer = new HudRenderer();
    hitEffects = new HitEffects();
    particleEffects = new ParticleEffects();
    motionTrailEffect = new MotionTrailEffect();
    audioManager = new AudioManager();
    bgShapes = new ShapeRenderer();
    frameNumber = 0;
}

// ---- render() 修改 ----
@Override
public void render() {
    float delta = Gdx.graphics.getDeltaTime();

    if (networkMode) {
        renderNetwork(delta);
    } else {
        renderDemo(delta);
    }
}

private void renderNetwork(float delta) {
    // GwtFrameSyncManager tick（驱动帧同步）
    GwtFrameSyncManager fsm = networkClient.getFrameSyncManager();
    if (fsm != null && fsm.isRunning()) {
        // 采样输入
        InputCommand cmd1 = inputHandler.samplePlayer1(frameNumber);
        networkClient.setCurrentLocalInput(cmd1);
        networkClient.submitInput(cmd1.up, cmd1.down, cmd1.left, cmd1.right,
                cmd1.punch, cmd1.kick, cmd1.special);
        fsm.tick(delta * 1000f);
    }

    // 渲染（与 demo 相同）
    Fighter p1 = gameWorld.getPlayer1();
    Fighter p2 = gameWorld.getPlayer2();

    // 特效
    if (!hitEffects.isInHitStop()) {
        int hp1Before = p1.getHealth();
        int hp2Before = p2.getHealth();
        int dmg1 = hp1Before - p1.getHealth();
        int dmg2 = hp2Before - p2.getHealth();
        // ...
    }

    hitEffects.update(delta);
    particleEffects.update(delta);
    motionTrailEffect.sample(p1, p2, delta);
    cameraController.update(p1, p2, delta);

    ScreenUtils.clear(0.08f, 0.08f, 0.12f, 1f);
    OrthographicCamera cam = cameraController.getCamera();
    drawBackground(cam);
    spriteRenderer.begin(cam);
    spriteRenderer.drawFighter(p1);
    spriteRenderer.drawFighter(p2);
    spriteRenderer.end();
    motionTrailEffect.render(cam);
    particleEffects.render(cam);
    hitEffects.render(cam);
    hudRenderer.render(gameWorld, cam);
}

// ---- URL 参数解析 ----
private static native String getUrlParam(String name) /*-{
    var match = $wnd.location.search.match(new RegExp('[?&]' + name + '=([^&]*)'));
    return match ? decodeURIComponent(match[1]) : null;
}-*/;

// ---- 现有 render() 重命名 ----
private void renderDemo(float delta) {
    // ... 现有 render() 的全部代码（重命名，不改变逻辑）...
}
```

> **注意：** 将现有 `create()` 中的代码提取到 `createDemoMode()`，现有 `render()` 中的代码提取到 `renderDemo()`。确保所有现有字段和 `dispose()`、`drawBackground()`、`triggerAudio()` 保持不变。

- [ ] **Step 2: Verify compile**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/Fyren/render/libgdx/gwt/FyrenGwtLauncher.java
git commit -m "feat: FyrenGwtLauncher supports network mode via WebSocket"
```

---

### Task 11: GWT compile and verify

**Files:**
- None (verification only)

- [ ] **Step 1: Download GWT SDK sources (if first time)**

```bash
mvn dependency:sources -DincludeArtifactIds=gdx-backend-gwt,gdx -q
```

- [ ] **Step 2: Run GWT compile**

```bash
mvn compile -q && ./gwt-compile.bat
```
Expected: GWT compile succeeds for all 5 browser permutations. Check output in `target/gwt-out/`.

- [ ] **Step 3: Verify no compilation errors in GWT output**

```bash
grep -i "error\|FAIL" target/gwt-out/fyren/compile.log 2>/dev/null | head -20 || echo "No compile log found, check console output"
```

Expected: No errors.

- [ ] **Step 4: Commit (if any fixes were needed)**

```bash
git add -A
git commit -m "fix: GWT compilation fixes for WebSocket network support"
```

---

## Post-Implementation Verification

After all tasks complete:

1. **Server startup test:** Start GameServer, verify WebSocket port 9878 is listening
2. **Unit tests:** `mvn test -q` — all pass
3. **Desktop client unchanged:** `java -cp target/classes com.Fyren.GameMain client localhost 9876 1 --preset kage` still works
4. **GWT compile:** All 5 browser permutations compile
5. **Browser client:** Open `target/gwt-out/index.html?mode=network&server=115.29.230.57&playerId=3` in browser → connects, matches, plays
