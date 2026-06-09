package com.Fyren.network;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * UDP游戏服务器 - 负责中转玩家输入、处理匹配请求、管理游戏会话
 *
 * 架构说明：
 * - 对于可直连的玩家，服务器仅做匹配撮合，之后玩家间P2P通信
 * - 对于NAT穿透失败的玩家，服务器作为中继转发游戏数据
 * - 心跳管理：定期检测客户端存活状态
 */
public class UdpServer {
    private final int port;
    private DatagramSocket socket;
    private volatile boolean running = false;
    private ExecutorService receiveExecutor;

    // 已连接的客户端信息
    private final ConcurrentHashMap<Integer, ClientSession> clients = new ConcurrentHashMap<>();

    // 活跃的游戏会话（匹配成功的玩家对）
    private final ConcurrentHashMap<String, GameSession> gameSessions = new ConcurrentHashMap<>();

    // 回调
    private BiConsumer<Packet, ClientSession> onPacketReceived;
    private Consumer<Exception> onError;
    private Consumer<Integer> onClientCountChanged;

    // 重传管理
    private final ConcurrentHashMap<Integer, PendingPacket> pendingPackets = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    // 客户端超时管理（30秒无心跳则断开）
    private static final long CLIENT_TIMEOUT_MS = 30_000;

    public UdpServer(int port) {
        this.port = port;
    }

    /**
     * 启动服务器
     */
    public void start() throws SocketException {
        socket = new DatagramSocket(port);
        socket.setSoTimeout(1000);
        running = true;

        receiveExecutor = Executors.newSingleThreadExecutor();
        scheduler = Executors.newScheduledThreadPool(2);

        receiveExecutor.submit(this::receiveLoop);
        scheduler.scheduleAtFixedRate(this::checkClientTimeouts, 0, 5, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::checkRetransmit, 0, 50, TimeUnit.MILLISECONDS);

        System.out.println("[UdpServer] 服务器启动，监听端口: " + port);
    }

    /**
     * 停止服务器
     */
    public void stop() {
        running = false;
        if (socket != null) socket.close();
        receiveExecutor.shutdown();
        scheduler.shutdown();
        System.out.println("[UdpServer] 服务器已停止");
    }

    /**
     * 主接收循环
     */
    private void receiveLoop() {
        byte[] buffer = new byte[2048];
        while (running) {
            try {
                DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                socket.receive(dp);

                byte[] data = new byte[dp.getLength()];
                System.arraycopy(buffer, 0, data, 0, dp.getLength());

                Packet packet = Packet.deserialize(data);
                if (packet == null) continue;

                InetSocketAddress senderAddr = new InetSocketAddress(dp.getAddress(), dp.getPort());

                // 根据包类型分发处理
                switch (packet.type) {
                    case HEARTBEAT:
                        handleHeartbeat((HeartbeatPacket) packet, senderAddr);
                        break;
                    case INPUT:
                        handleInput((InputPacket) packet, senderAddr);
                        break;
                    case MATCH_REQ:
                        handleMatchRequest((MatchRequestPacket) packet, senderAddr);
                        break;
                    case ACK:
                        handleAck((AckPacket) packet);
                        break;
                    default:
                        if (onPacketReceived != null) {
                            ClientSession session = findSessionByAddress(senderAddr);
                            if (session != null) {
                                onPacketReceived.accept(packet, session);
                            }
                        }
                }

            } catch (SocketTimeoutException e) {
                // 正常超时，继续
            } catch (Exception e) {
                System.err.println("[UdpServer] 接收循环异常: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * 处理客户端心跳
     */
    private void handleHeartbeat(HeartbeatPacket packet, InetSocketAddress addr) {
        ClientSession session = findSessionByAddress(addr);
        if (session != null) {
            session.lastHeartbeat = System.currentTimeMillis();

            // 计算RTT
            long rtt = System.currentTimeMillis() - packet.pingTime;
            session.rtt = rtt;

            // 回复心跳（让客户端也能计算RTT）
            HeartbeatPacket reply = new HeartbeatPacket(generateSequence(), packet.pingTime);
            sendTo(reply, addr);
        }
    }

    /**
     * 处理输入包 — 转发给对手
     */
    private void handleInput(InputPacket packet, InetSocketAddress addr) {
        ClientSession session = findSessionByAddress(addr);
        if (session == null || session.opponentAddress == null) return;

        // 发送ACK确认
        AckPacket ack = new AckPacket(generateSequence(), packet.sequence);
        sendTo(ack, addr);

        // 转发输入给对手
        sendTo(packet, session.opponentAddress);
    }

    /**
     * 处理匹配请求
     */
    private void handleMatchRequest(MatchRequestPacket packet, InetSocketAddress addr) {
        // 注册或更新客户端信息（检查是否是新客户端）
        boolean isNewClient = !clients.containsKey(packet.playerId);
        ClientSession session = clients.computeIfAbsent(packet.playerId, ClientSession::new);
        session.address = addr;
        session.rating = packet.playerRating;
        session.lastHeartbeat = System.currentTimeMillis();

        if (isNewClient) {
            notifyClientCountChanged();
        }

        System.out.println("[UdpServer] 收到匹配请求: playerId=" + packet.playerId +
                ", rating=" + packet.playerRating + ", addr=" + addr);

        // 委托给MatchManager处理（通过回调）
        if (onPacketReceived != null) {
            onPacketReceived.accept(packet, session);
        }
    }

    /**
     * 处理ACK包
     */
    private void handleAck(AckPacket packet) {
        pendingPackets.remove(packet.ackedSequence);
    }

    /**
     * 向指定地址发送数据包（可靠传输）
     */
    public void sendReliableTo(Packet packet, InetSocketAddress target) {
        try {
            byte[] data = packet.serialize();
            DatagramPacket dp = new DatagramPacket(data, data.length, target);
            socket.send(dp);

            pendingPackets.put(packet.sequence,
                    new PendingPacket(data, System.currentTimeMillis(), packet.sequence, target));
        } catch (IOException e) {
            if (onError != null) onError.accept(e);
        }
    }

    /**
     * 向指定地址发送数据包（不可靠传输）
     */
    public void sendTo(Packet packet, InetSocketAddress target) {
        try {
            byte[] data = packet.serialize();
            DatagramPacket dp = new DatagramPacket(data, data.length, target);
            socket.send(dp);
        } catch (IOException e) {
            if (onError != null) onError.accept(e);
        }
    }

    /**
     * 向指定玩家发送数据包
     */
    public void sendToPlayer(Packet packet, int playerId) {
        ClientSession session = clients.get(playerId);
        if (session != null && session.address != null) {
            sendTo(packet, session.address);
        }
    }

    /**
     * 通知客户端匹配结果
     */
    public void notifyMatchResult(int playerId, MatchResponsePacket response) {
        sendReliableTo(response, clients.get(playerId).address);
    }

    /**
     * 建立游戏会话（两个玩家配对成功）
     */
    public void createGameSession(int player1Id, int player2Id) {
        ClientSession p1 = clients.get(player1Id);
        ClientSession p2 = clients.get(player2Id);

        if (p1 == null || p2 == null) return;

        // 交换双方地址信息
        p1.opponentAddress = p2.address;
        p2.opponentAddress = p1.address;
        p1.opponentId = player2Id;
        p2.opponentId = player1Id;

        String sessionKey = generateSessionKey(player1Id, player2Id);
        GameSession session = new GameSession(sessionKey, player1Id, player2Id);
        gameSessions.put(sessionKey, session);

        System.out.println("[UdpServer] 游戏会话建立: " + sessionKey);
    }

    /**
     * 根据地址查找客户端会话
     */
    private ClientSession findSessionByAddress(InetSocketAddress addr) {
        for (ClientSession session : clients.values()) {
            if (session.address != null && session.address.equals(addr)) {
                return session;
            }
        }
        return null;
    }

    /**
     * 定期检查客户端超时
     */
    private void checkClientTimeouts() {
        long now = System.currentTimeMillis();
        List<Integer> timeoutIds = new ArrayList<>();

        for (Map.Entry<Integer, ClientSession> entry : clients.entrySet()) {
            if (now - entry.getValue().lastHeartbeat > CLIENT_TIMEOUT_MS) {
                timeoutIds.add(entry.getKey());
            }
        }

        for (int id : timeoutIds) {
            System.out.println("[UdpServer] 客户端超时断开: playerId=" + id);
            clients.remove(id);
            // 清理相关游戏会话
            gameSessions.entrySet().removeIf(e -> {
                GameSession gs = e.getValue();
                return gs.player1Id == id || gs.player2Id == id;
            });
        }

        if (!timeoutIds.isEmpty()) {
            notifyClientCountChanged();
        }
    }

    /**
     * 检查并执行重传
     */
    private void checkRetransmit() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<Integer, PendingPacket>> it = pendingPackets.entrySet().iterator();
             it.hasNext(); ) {
            Map.Entry<Integer, PendingPacket> entry = it.next();
            PendingPacket pp = entry.getValue();
            if (now - pp.sendTime > 100) {
                if (pp.retryCount >= 5) {
                    it.remove();
                    continue;
                }
                try {
                    DatagramPacket dp = new DatagramPacket(pp.data, pp.data.length, pp.target);
                    socket.send(dp);
                    pp.sendTime = now;
                    pp.retryCount++;
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 生成会话键
     */
    private String generateSessionKey(int p1, int p2) {
        return Math.min(p1, p2) + "_" + Math.max(p1, p2);
    }

    private static int sequenceGenerator = 0;
    private static synchronized int generateSequence() {
        return ++sequenceGenerator;
    }

    // ========== 内部类 ==========

    /**
     * 客户端会话信息
     */
    public static class ClientSession {
        public final int playerId;
        public InetSocketAddress address;
        public InetSocketAddress opponentAddress;
        public int opponentId;
        public int rating;
        public long lastHeartbeat;
        public long rtt;

        public ClientSession(int playerId) {
            this.playerId = playerId;
            this.lastHeartbeat = System.currentTimeMillis();
        }

        public boolean isInMatch() {
            return opponentAddress != null;
        }
    }

    /**
     * 游戏会话
     */
    public static class GameSession {
        public final String sessionKey;
        public final int player1Id;
        public final int player2Id;
        public long startTime;

        public GameSession(String sessionKey, int player1Id, int player2Id) {
            this.sessionKey = sessionKey;
            this.player1Id = player1Id;
            this.player2Id = player2Id;
            this.startTime = System.currentTimeMillis();
        }
    }

    /**
     * 待确认包
     */
    private static class PendingPacket {
        byte[] data;
        long sendTime;
        int retryCount = 0;
        int sequence;
        InetSocketAddress target;

        PendingPacket(byte[] data, long sendTime, int sequence, InetSocketAddress target) {
            this.data = data;
            this.sendTime = sendTime;
            this.sequence = sequence;
            this.target = target;
        }
    }

    // ========== Getters / Setters ==========

    public void setOnPacketReceived(BiConsumer<Packet, ClientSession> callback) {
        this.onPacketReceived = callback;
    }

    public void setOnError(Consumer<Exception> callback) {
        this.onError = callback;
    }

    public void setOnClientCountChanged(Consumer<Integer> callback) {
        this.onClientCountChanged = callback;
    }

    private void notifyClientCountChanged() {
        if (onClientCountChanged != null) {
            onClientCountChanged.accept(clients.size());
        }
    }

    public ConcurrentHashMap<Integer, ClientSession> getClients() {
        return clients;
    }

    public ConcurrentHashMap<String, GameSession> getGameSessions() {
        return gameSessions;
    }

    public int getPort() {
        return port;
    }

}
