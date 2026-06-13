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
