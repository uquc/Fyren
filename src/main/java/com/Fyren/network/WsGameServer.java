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
        // 只处理二进制消息
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
