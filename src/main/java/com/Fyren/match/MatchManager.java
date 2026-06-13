package com.Fyren.match;

import com.Fyren.network.*;
import com.Fyren.network.UdpServer.ClientSession;

import java.util.concurrent.*;

/**
 * 匹配管理器 — 协调匹配请求与服务器通信
 *
 * 职责：
 * 1. 接收客户端的匹配请求
 * 2. 管理玩家的MMR数据
 * 3. 将玩家加入匹配队列
 * 4. 配对成功后通知双方玩家
 * 5. 处理匹配取消
 */
public class MatchManager {
    private final UdpServer server;
    private final Matchmaker matchmaker;

    // 玩家评分数据
    private final ConcurrentHashMap<Integer, PlayerRating> playerRatings = new ConcurrentHashMap<>();

    // 玩家角色预设（匹配请求时存储，匹配成功时转发）
    private final ConcurrentHashMap<Integer, Integer> playerPresets = new ConcurrentHashMap<>();

    // 序列号生成
    private int sequenceCounter = 0;

    // 匹配生命周期回调（由 GameServer 注入，用于追踪活跃匹配数）
    private Runnable onMatchCreated;
    private Runnable onMatchEnded;

    public void setOnMatchCreated(Runnable callback) { this.onMatchCreated = callback; }
    public void setOnMatchEnded(Runnable callback) { this.onMatchEnded = callback; }

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

    public MatchManager(UdpServer server) {
        this.server = server;
        this.matchmaker = new Matchmaker();
    }

    /**
     * 启动匹配管理器
     */
    public void start() {
        // 设置匹配成功回调
        matchmaker.setOnMatchFound((p1, p2) -> {
            handleMatchFound(p1, p2);
        });
        matchmaker.start();
        System.out.println("[MatchManager] 匹配管理器已启动");
    }

    /**
     * 停止匹配管理器
     */
    public void stop() {
        matchmaker.stop();
    }

    /**
     * 处理客户端的匹配请求
     */
    public void handleMatchRequest(MatchRequestPacket packet, ClientSession session) {
        int playerId = packet.playerId;
        int rating = packet.playerRating;

        // 如果玩家已在游戏中（已有对手），忽略重传的匹配请求
        if (session.opponentAddress != null) {
            return;
        }

        // 存储角色预设
        playerPresets.put(playerId, packet.presetOrdinal);

        // 确保玩家评分数据存在
        playerRatings.computeIfAbsent(playerId, k -> new PlayerRating(playerId, rating));

        // 加入匹配队列
        matchmaker.enqueue(playerId, rating);

        // 回复"等待中"
        MatchResponsePacket response = MatchResponsePacket.waiting(nextSequence());
        server.sendToPlayer(response, playerId);
    }

    /**
     * 取消匹配
     */
    public void cancelMatch(int playerId) {
        matchmaker.dequeue(playerId);

        // 通知取消
        MatchResponsePacket response = new MatchResponsePacket(
                nextSequence(), MatchResponsePacket.STATUS_CANCELLED, 0, 0, "", 0, 0);
        server.sendToPlayer(response, playerId);
    }

    /**
     * 匹配成功后的处理
     */
    private void handleMatchFound(Matchmaker.MatchEntry p1, Matchmaker.MatchEntry p2) {
        // 建立服务器端游戏会话
        server.createGameSession(p1.playerId, p2.playerId);

        // 获取双方的地址信息
        ClientSession session1 = server.getClients().get(p1.playerId);
        ClientSession session2 = server.getClients().get(p2.playerId);

        if (session1 == null || session2 == null) {
            System.out.println("[MatchManager] 匹配失败：客户端会话丢失");
            return;
        }

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
    }

    /**
     * 通知匹配结束（由 GameServer 在收到 ResultPacket 时调用）
     */
    public void notifyMatchEnded() {
        if (onMatchEnded != null) onMatchEnded.run();
    }

    /**
     * 比赛结束后更新MMR
     *
     * @param winnerId 胜者ID（-1表示平局）
     * @param loserId  负者ID
     */
    public void reportMatchResult(int player1Id, int player2Id, int winnerId) {
        PlayerRating rating1 = playerRatings.get(player1Id);
        PlayerRating rating2 = playerRatings.get(player2Id);

        if (rating1 == null || rating2 == null) return;

        if (winnerId == -1) {
            // 平局
            rating1.updateRating(rating2, 0.5);
            rating2.updateRating(rating1, 0.5);
        } else if (winnerId == player1Id) {
            rating1.updateRating(rating2, 1.0);
            rating2.updateRating(rating1, 0.0);
        } else {
            rating1.updateRating(rating2, 0.0);
            rating2.updateRating(rating1, 1.0);
        }
    }

    /**
     * 获取玩家评分
     */
    public PlayerRating getPlayerRating(int playerId) {
        return playerRatings.get(playerId);
    }

    private synchronized int nextSequence() {
        return ++sequenceCounter;
    }

    // ========== Getters ==========

    public Matchmaker getMatchmaker() { return matchmaker; }
    public ConcurrentHashMap<Integer, PlayerRating> getPlayerRatings() { return playerRatings; }
}
