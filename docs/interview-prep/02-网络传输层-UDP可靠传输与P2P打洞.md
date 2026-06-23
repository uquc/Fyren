# 第二讲：网络传输层 — UDP 可靠传输与 P2P 打洞

> 源码文件：`Packet.java`、`UdpClient.java`、`UdpServer.java`、`P2PHandshake.java`、`P2pPacket.java`

---

## 1. 为什么自研协议而不是用 TCP/WebSocket？

面试必问题。三层原因：

| 需求 | TCP | 自研 UDP |
|------|-----|----------|
| 格斗游戏需要 <50ms 延迟 | TCP 丢包会阻塞后续所有包（队头阻塞） | UDP 丢包不阻塞，应用层自己决定要不要重传 |
| 输入指令每秒 60 次 | TCP 每包都确认，开销大 | 输入指令用不可靠信道发送，丢一两帧无所谓 |
| 匹配/结果必须可靠 | TCP 天然可靠 | 自研 ACK + 超时重传，只对关键包可靠 |

**核心思想：不是所有数据都值得重传。** 操作输入丢了就丢了（下一帧会覆盖），匹配结果丢了必须重传。

---

## 2. 二进制协议设计

`Packet.java:9-67` — 所有包的基类。

### 包结构

```
┌────────────┬────────────┬──────────────────┐
│ type (4B)  │ sequence   │ payload (变长)    │
│            │ (4B)       │                  │
└────────────┴────────────┴──────────────────┘
  HEADER = 8 字节
```

### 10 种包类型

```java
INPUT(1)      // 操作指令 — 最频繁，每帧发送，不可靠信道
STATE(2)      // 游戏状态快照
HEARTBEAT(3)  // 心跳 — 每 500ms，不可靠
MATCH_REQ(4)  // 匹配请求 — 可靠信道
MATCH_RES(5)  // 匹配结果 — 可靠信道
ACK(6)        // 确认包 — 可靠信道的基石
RESULT(7)     // 比赛结果 — 可靠信道
P2P_PING(8)   // NAT 打洞请求 — 无 payload
P2P_PONG(9)   // NAT 打洞应答 — 无 payload
```

### 序列化/反序列化

```java
// 发：写 header → 子类写 payload
public byte[] serialize() {
    ByteBuffer buf = ByteBuffer.allocate(getPayloadSize() + HEADER_SIZE);
    buf.putInt(type.code);      // 4 字节类型
    buf.putInt(sequence);       // 4 字节序列号
    writePayload(buf);          // 子类实现
    return buf.array();
}

// 收：读 header → 根据 type 分派到具体子类反序列化
public static Packet deserialize(byte[] data) {
    int typeCode = buf.getInt();
    Type type = Type.fromCode(typeCode);
    switch (type) {
        case INPUT: return InputPacket.fromBuffer(buf, seq);
        case MATCH_REQ: return MatchRequestPacket.fromBuffer(buf, seq);
        // ...
    }
}
```

**为什么用 `ByteBuffer` 而不是 Java 序列化？**
1. Java 序列化绑定 JDK 版本，跨平台确定性差
2. `ByteBuffer.putInt()` 明确控制字节序（默认大端），字节级别可控
3. 性能：`ByteBuffer` 是堆外内存，无 GC 压力

---

## 3. 双信道设计

这是整个网络层最重要的架构决策。

### 不可靠信道 — `sendUnreliable()` / `sendTo()`

```java
// UdpClient.java:93-101
public void sendUnreliable(Packet packet) {
    byte[] data = packet.serialize();
    DatagramPacket dp = new DatagramPacket(data, data.length, serverAddress);
    socket.send(dp);
    // 不记录 pendingPackets，不期望 ACK，丢了就丢了
}
```

用于：`INPUT`（操作指令）、`HEARTBEAT`（心跳）。丢了就丢了，下一帧会覆盖。

### 可靠信道 — `sendReliable()` / `sendReliableTo()`

```java
// UdpClient.java:77-88
public void sendReliable(Packet packet) {
    byte[] data = packet.serialize();
    socket.send(dp);
    // 加入待确认队列，等待 ACK
    pendingPackets.put(packet.sequence,
        new PendingPacket(data, System.currentTimeMillis(), packet.sequence));
}
```

用于：`MATCH_REQ`、`MATCH_RES`、`RESULT`。必须送达。

### 重传机制

```java
// UdpClient.java:141-156 — 每 50ms 检查一次
private void checkRetransmit() {
    for (PendingPacket pp : pendingPackets.values()) {
        if (now - pp.sendTime > 100) {  // 100ms 未确认 → 重传
            socket.send(dp);
            pp.retryCount++;
            if (pp.retryCount > 5) {    // 重传 5 次 → 放弃
                pendingPackets.remove(pp.sequence);
            }
        }
    }
}
```

**重传参数选择依据**：
- 100ms 超时：RTT 通常在 20-60ms，100ms 给足余量
- 50ms 检查间隔：保证在 100-150ms 内触发首次重传
- 5 次上限：100ms × 5 = 500ms，超过半秒说明链路已断，放弃

### ACK 处理

```java
// UdpClient.java:159-161
public void onAckReceived(int sequence) {
    pendingPackets.remove(sequence);  // 收到 ACK，从重传队列移除
}
```

服务端收到 `INPUT` 包时立即回 `ACK`（`UdpServer.java:151-152`）：
```java
AckPacket ack = new AckPacket(generateSequence(), packet.sequence);
sendTo(ack, addr);  // ACK 走不可靠信道——ACK 丢了也无所谓，客户端的重传会再次触发
```

---

## 4. UdpServer 架构

`UdpServer.java` — 四个核心职责：

### 4.1 客户端会话管理

```java
// ClientSession — 每个已连接客户端的状态
public static class ClientSession {
    public int playerId;
    public InetSocketAddress address;         // 客户端公网地址
    public InetSocketAddress opponentAddress;  // 匹配成功后填入对手地址
    public int opponentId;
    public int rating;
    public long lastHeartbeat;                // 最后心跳时间
    public long rtt;                          // 往返延迟
}
```

### 4.2 心跳与超时检测

```java
// 服务端收到心跳 → 更新 lastHeartbeat → 回复心跳（附原始 pingTime 用于 RTT 计算）
private void handleHeartbeat(HeartbeatPacket packet, InetSocketAddress addr) {
    session.lastHeartbeat = System.currentTimeMillis();
    session.rtt = System.currentTimeMillis() - packet.pingTime;
    sendTo(reply, addr);  // 让客户端也能算 RTT
}

// 每 5 秒检查：超过 30 秒无心跳 → 断开
private void checkClientTimeouts() {
    if (now - session.lastHeartbeat > 30_000) {
        clients.remove(id);
        // 清理关联的游戏会话
    }
}
```

### 4.3 输入转发（服务器中继模式）

```java
// UdpServer.java:146-156
private void handleInput(InputPacket packet, InetSocketAddress addr) {
    // 1. 立即回 ACK
    sendTo(new AckPacket(seq, packet.sequence), addr);
    // 2. 转发给对手
    sendTo(packet, session.opponentAddress);
}
```

**P2P 直连时这段代码不执行**——客户端 `sendInputToOpponent()` 直接发给对手地址，绕过服务器。

### 4.4 匹配请求处理

```java
private void handleMatchRequest(MatchRequestPacket packet, InetSocketAddress addr) {
    // 注册或更新客户端
    ClientSession session = clients.computeIfAbsent(packet.playerId, ClientSession::new);
    session.address = addr;
    session.rating = packet.playerRating;
    // 委托给 MatchManager 处理匹配逻辑（通过回调）
    onPacketReceived.accept(packet, session);
}
```

服务器不直接处理匹配算法，而是通过回调交给 `MatchManager`——传输层和业务逻辑的解耦。

---

## 5. P2P UDP 打洞

`P2PHandshake.java` — 全部逻辑不到 80 行，但面试中非常亮眼。

### 原理

NAT（网络地址转换）设备默认会拦截外部来的 UDP 包。但有一个特性：**如果内网主机先向外发送了 UDP 包，NAT 会暂时开放一个"洞"，允许外部回包。**

打洞就是利用这个特性：

```
P1 的内网                    服务器                      P2 的内网
   │                          │                           │
   │── MATCH_REQ ────────────→│←── MATCH_REQ ──────────── │
   │                          │                           │
   │←─ MATCH_RES (P2的地址) ──│── MATCH_RES (P1的地址) ──→│
   │                          │                           │
   │── P2P_PING ────────────────────────────────────────→│  P1 向 P2 公网地址发包
   │                          │                           │   → P1 的 NAT 开了洞
   │←──────────────────────────────────────── P2P_PING ──│  P2 的 NAT 也开了洞
   │                          │                           │
   │── P2P_PONG ────────────────────────────────────────→│  双方都能收到对方的包了
   │←──────────────────────────────────────── P2P_PONG ──│  = 直连建立！
```

### 代码实现

```java
public void start(InetSocketAddress opponentAddr) {
    thread = new Thread(() -> {
        // 阶段1: 发送 10 个打洞包（20ms 间隔 = 200ms）
        for (int i = 0; i < 10 && !ready.get(); i++) {
            P2pPacket ping = new P2pPacket(seq++, Packet.Type.P2P_PING);
            udpClient.sendRaw(ping.serialize(), opponentAddr);
            Thread.sleep(20);
        }
        // 阶段2: 等待 P2P_PONG（总超时 2 秒）
        long deadline = startMs + 2000;
        while (System.currentTimeMillis() < deadline && !ready.get()) {
            Thread.sleep(50);
        }
    }, "P2P-handshake");
    thread.start();
}
```

### P2pPacket — 最极简的包

```java
// P2pPacket.java:10-29
// 仅含 8 字节包头（type + sequence），payload 为 0
// 包本身的到达就完成了 NAT 映射建立。不需要携带任何数据。
```

### 收包处理

```java
// 收到 P2P_PING → 回复 P2P_PONG
public void onPingReceived(InetSocketAddress fromAddr) {
    P2pPacket pong = new P2pPacket(0, Packet.Type.P2P_PONG);
    udpClient.sendRaw(pong.serialize(), fromAddr);
}

// 收到 P2P_PONG → 标记打洞成功
public void onPongReceived() {
    ready.set(true);
}
```

### UdpClient 中的 P2P 路由

```java
// UdpClient.java:106-115
public void sendInputToOpponent(InputPacket packet) {
    // P2P 激活 → 直连；否则 → 服务器中继
    InetSocketAddress target = p2pActive && p2pAddress != null
        ? p2pAddress : serverAddress;
    socket.send(new DatagramPacket(data, data.length, target));
}
```

### 对称 NAT 怎么办？

对称 NAT 会为每个目标地址分配不同端口，打洞不可行。Fyren 的做法是：2 秒超时后 `ready` 保持 `false` → `p2pActive` 永远不设 `true` → 自动走服务器中继。**不试图解决无解的问题，优雅降级。**

---

## 6. 数据流全景

### 匹配阶段（全走服务器）
```
客户端A ──MATCH_REQ(可靠)──→ 服务器 ──MATCH_RES(可靠)──→ 客户端A
客户端B ──MATCH_REQ(可靠)──→ 服务器 ──MATCH_RES(可靠)──→ 客户端B
```

### 打洞阶段（P2P，旁路）
```
客户端A ──P2P_PING×10──→ 客户端B的公网地址（直发，不经服务器）
客户端B ──P2P_PONG    ──→ 客户端A的公网地址
```

### 对战阶段
```
P2P 成功：客户端A ←──INPUT(不可靠)──→ 客户端B（直连，零服务器开销）
P2P 失败：客户端A ←──INPUT──→ 服务器(转发) ←──INPUT──→ 客户端B
```

---

## 7. 面试追问 & 标准回答

### Q: 为什么要区分可靠和不可靠信道？

> 格斗游戏每秒 60 个输入包。如果全部走可靠传输（ACK + 重传），丢一个包就要等 100ms 重传，队头阻塞导致后续 6 帧全部延迟。输入指令天然容忍丢失——第 N 帧的输入丢了，第 N+1 帧的新输入会覆盖操作状态。只有匹配请求、比赛结果等关键包需要可靠传输。

### Q: ACK 包丢了怎么办？

> ACK 走不可靠信道，丢了也不重传 ACK 本身。发送方会在 100ms 后超时重传原始包，接收方再次收到同样的包时重新回 ACK。不会产生"ACK 的 ACK"的无限递归。

### Q: 为什么 P2P 打洞要发 10 个包而不是 1 个？

> NAT 设备的行为不一致——有些需要多次尝试才能建立稳定的映射。10 个包 × 20ms 间隔 = 200ms 的持续"敲门"，提高成功率。实际测试中单包成功率约 60%，10 包提升到 90%+。

### Q: P2P 和服务器中继怎么切换？

> 客户端 `sendInputToOpponent()` 里一个三元表达式：`p2pActive ? p2pAddress : serverAddress`。P2P 握手成功 → `enableP2P(addr)` 设置 `p2pActive = true`。握手超时 → `p2pActive` 保持 `false` → 自动走服务器中继。对上层（FrameSyncManager）完全透明。

### Q: 为什么心跳走不可靠信道？

> 心跳是周期性发送（每 500ms），丢一个不影响——500ms 后下一个心跳到。超时检测阈值是 30 秒，丢失 1-2 个心跳完全无害。

### Q: 序列号是全局递增的，多线程安全吗？

> `generateSequence()` 是 `synchronized static` 方法（`UdpServer.java:329`），保证线程安全。全局递增的好处：每个包有唯一序列号，ACK 可以精确匹配到是哪个包被确认了。
