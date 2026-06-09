package com.Fyren;

import com.Fyren.game.FighterPreset;
import com.Fyren.game.GameWorld;
import com.Fyren.match.PlayerRating;
import com.Fyren.network.*;
import com.Fyren.sync.FrameSyncManager;
import com.Fyren.sync.InputCommand;
import com.Fyren.util.InputCodec;

import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 游戏客户端 — 整合网络通信、帧同步和游戏逻辑
 *
 * 架构：
 *   GameClient
 *   ├── UdpClient          — UDP网络通信
 *   ├── FrameSyncManager   — 帧同步引擎（锁步+回滚）
 *   ├── GameWorld          — 游戏世界（逻辑+物理）
 *   └── PlayerRating       — 本地玩家的MMR
 *
 * 生命周期：
 *   1. 创建客户端并连接服务器
 *   2. 发送匹配请求
 *   3. 等待匹配结果
 *   4. 匹配成功后，与对手建立P2P连接
 *   5. 开始帧同步游戏循环
 *   6. 游戏结束后上报结果
 */
public class GameClient {
    // 服务器地址
    private final String serverHost;
    private final int serverPort;

    // 本地玩家
    private final int localPlayerId;
    private final FighterPreset preset;
    private final PlayerRating playerRating;

    // 核心组件
    private UdpClient udpClient;
    private GameWorld gameWorld;
    private FrameSyncManager frameSyncManager;

    // 状态
    private volatile ClientState state = ClientState.IDLE;
    private volatile int opponentId = -1;
    private volatile int opponentPresetOrdinal = 1; // 默认TAKESHI
    private volatile boolean opponentReady = false;

    // 帧计数器（用于输入发送）
    private final AtomicInteger frameCounter = new AtomicInteger(0);
    private int sequenceCounter = 0;

    // 当前帧的本地输入（由KeyInputHandler设置，由FrameSyncManager回调读取）
    private volatile InputCommand currentLocalInput = null;

    // 线程安全锁 — FrameSyncManager 回滚时持写锁，渲染线程读时持读锁
    private final ReentrantReadWriteLock worldLock = new ReentrantReadWriteLock();

    // 回调
    private GameEventCallback callback;

    // 认证 token
    private String accessToken;
    private String refreshToken;

    // ========== 状态枚举 ==========

    public enum ClientState {
        IDLE,           // 空闲
        CONNECTING,     // 连接中
        CONNECTED,      // 已连接服务器
        MATCHING,       // 匹配中
        MATCHED,        // 匹配成功，准备对局
        PLAYING,        // 对局中
        GAME_OVER,      // 对局结束
        DISCONNECTED    // 已断开
    }

    // ========== 回调接口 ==========

    public interface GameEventCallback {
        void onStateChanged(ClientState newState);
        void onMatchFound(int opponentId, int opponentRating);
        void onGameStart();
        void onGameOver(int winnerId);
        void onError(Exception e);
    }

    // ========== 构造函数 ==========

    public GameClient(String serverHost, int serverPort, int localPlayerId, FighterPreset preset) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.localPlayerId = localPlayerId;
        this.preset = preset;
        this.playerRating = new PlayerRating(localPlayerId);
        this.gameWorld = new GameWorld();
    }

    /**
     * 使用初始MMR构造
     */
    public GameClient(String serverHost, int serverPort, int localPlayerId, int initialRating, FighterPreset preset) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.localPlayerId = localPlayerId;
        this.preset = preset;
        this.playerRating = new PlayerRating(localPlayerId, initialRating);
        this.gameWorld = new GameWorld();
    }

    // ========== 认证 ==========

    /** HTTP 登录，获取 JWT token 对 */
    public static class AuthResult {
        public final boolean success;
        public final int userId;
        public final String username;
        public final int mmr;
        public final String accessToken;
        public final String refreshToken;
        public final String error;

        private AuthResult(boolean success, int userId, String username, int mmr,
                           String accessToken, String refreshToken, String error) {
            this.success = success;
            this.userId = userId;
            this.username = username;
            this.mmr = mmr;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.error = error;
        }
    }

    /** 向认证服务器注册 */
    public static AuthResult register(String authHost, int authPort, String username, String password) {
        try {
            String body = String.format("{\"username\":\"%s\",\"password\":\"%s\"}",
                    escapeJson(username), escapeJson(password));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + authHost + ":" + authPort + "/auth/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 201) {
                // 手动解析 JSON
                String r = resp.body();
                int userId = extractInt(r, "userId");
                String uname = extractString(r, "username");
                return new AuthResult(true, userId, uname, 1000, null, null, null);
            } else {
                String err = extractString(resp.body(), "error");
                return new AuthResult(false, 0, null, 0, null, null, err != null ? err : "注册失败");
            }
        } catch (Exception e) {
            return new AuthResult(false, 0, null, 0, null, null, e.getMessage());
        }
    }

    /** 向认证服务器登录，返回 token 对 */
    public static AuthResult login(String authHost, int authPort, String username, String password) {
        try {
            String body = String.format("{\"username\":\"%s\",\"password\":\"%s\"}",
                    escapeJson(username), escapeJson(password));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + authHost + ":" + authPort + "/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String r = resp.body();
                String at = extractString(r, "accessToken");
                String rt = extractString(r, "refreshToken");
                int uid = extractInt(r, "userId");
                String uname = extractString(r, "username");
                int mmr = extractInt(r, "mmr");
                return new AuthResult(true, uid, uname, mmr, at, rt, null);
            } else {
                String err = extractString(resp.body(), "error");
                return new AuthResult(false, 0, null, 0, null, null, err != null ? err : "登录失败");
            }
        } catch (Exception e) {
            return new AuthResult(false, 0, null, 0, null, null, e.getMessage());
        }
    }

    /** 设置当前会话的 token */
    public void setTokens(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public String getAccessToken() { return accessToken; }

    // === 简易 JSON 解析（零依赖） ===

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String extractString(String json, String key) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        java.util.regex.Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static int extractInt(String json, String key) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)");
        java.util.regex.Matcher m = p.matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    // ========== 生命周期 ==========

    /**
     * 连接服务器
     */
    public void connect() throws SocketException {
        setState(ClientState.CONNECTING);

        udpClient = new UdpClient(serverHost, serverPort);
        setupPacketHandler();

        udpClient.start();
        setState(ClientState.CONNECTED);

        System.out.println("[GameClient] 已连接到服务器 " + serverHost + ":" + serverPort);
    }

    /**
     * 请求匹配
     */
    public void requestMatch() {
        if (state != ClientState.CONNECTED) {
            System.err.println("[GameClient] 未连接到服务器，无法请求匹配");
            return;
        }

        setState(ClientState.MATCHING);

        MatchRequestPacket req = new MatchRequestPacket(
                nextSequence(), localPlayerId, playerRating.getRating(), preset.ordinal());
        udpClient.sendReliable(req);

        System.out.println("[GameClient] 已发送匹配请求 (rating=" + playerRating.getRating() + ")");
    }

    /**
     * 取消匹配
     */
    public void cancelMatch() {
        if (state != ClientState.MATCHING) return;

        MatchRequestPacket cancel = new MatchRequestPacket(nextSequence(), localPlayerId, -1);
        udpClient.sendReliable(cancel);
        setState(ClientState.CONNECTED);
    }

    /**
     * 开始游戏（匹配成功后调用）
     */
    public void startGame() {
        if (state != ClientState.MATCHED) {
            System.err.println("[GameClient] 尚未匹配成功，无法开始游戏");
            return;
        }

        setState(ClientState.PLAYING);

        // 初始化游戏世界（使用协商后的双方角色预设）
        FighterPreset oppPreset = FighterPreset.values()[opponentPresetOrdinal];
        gameWorld.setupPlayers(preset, oppPreset);

        // 初始化帧同步管理器（只创建一次）
        frameSyncManager = new FrameSyncManager(gameWorld);
        frameSyncManager.setLocalPlayerId(localPlayerId);
        frameSyncManager.setLocalInputProvider(this::getCurrentLocalInput);
        frameSyncManager.start();

        if (callback != null) callback.onGameStart();
        System.out.println("[GameClient] 游戏开始! 对手ID=" + opponentId);
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        if (frameSyncManager != null) frameSyncManager.stop();
        if (udpClient != null) udpClient.stop();
        setState(ClientState.DISCONNECTED);
        System.out.println("[GameClient] 已断开连接");
    }

    // ========== 输入处理 ==========

    /**
     * 供 FrameSyncManager 回调 — 获取当前帧的本地输入
     */
    private InputCommand getCurrentLocalInput(int frameNumber, int localPlayerId) {
        InputCommand cmd = currentLocalInput;
        if (cmd == null) {
            return new InputCommand(frameNumber, localPlayerId);
        }
        cmd.frameNumber = frameNumber;
        return cmd;
    }

    /**
     * 设置当前输入（供 KeyInputHandler/SwingGameWindow 调用）
     */
    public void setCurrentLocalInput(InputCommand cmd) {
        this.currentLocalInput = cmd;
    }

    /**
     * 提交本地玩家输入（控制台/demo模式用）
     */
    public void submitInput(boolean up, boolean down, boolean left, boolean right,
                            boolean punch, boolean kick, boolean special) {
        if (state != ClientState.PLAYING) return;
        InputCommand cmd = new InputCommand(0, localPlayerId);
        cmd.up = up; cmd.down = down; cmd.left = left; cmd.right = right;
        cmd.punch = punch; cmd.kick = kick; cmd.special = special;
        this.currentLocalInput = cmd;
        sendInputToOpponent(cmd);
    }

    /**
     * 发送输入到对手（供 SwingGameWindow/控制台 调用）
     */
    public void sendInputToOpponent(InputCommand cmd) {
        if (cmd == null || cmd.isEmpty()) return;
        InputPacket packet = InputCodec.encode(cmd, nextSequence());
        udpClient.sendUnreliable(packet);
    }

    // ========== 网络处理 ==========

    /**
     * 设置数据包处理器
     */
    private void setupPacketHandler() {
        udpClient.setOnPacketReceived(packet -> {
            try {
                handlePacket(packet);
            } catch (Exception e) {
                if (callback != null) callback.onError(e);
            }
        });

        udpClient.setOnError(e -> {
            System.err.println("[GameClient] 网络错误: " + e.getMessage());
            if (callback != null) callback.onError(e);
        });
    }

    /**
     * 处理收到的数据包
     */
    private void handlePacket(Packet packet) {
        switch (packet.type) {
            case INPUT:
                handleInputPacket((InputPacket) packet);
                break;
            case MATCH_RES:
                handleMatchResponse((MatchResponsePacket) packet);
                break;
            case ACK:
                udpClient.onAckReceived(((AckPacket) packet).ackedSequence);
                break;
            case HEARTBEAT:
                // 心跳包已由UdpClient内部处理
                break;
            case STATE:
                // 状态同步包（用于观战或断线重连）
                handleStatePacket((StatePacket) packet);
                break;
        }
    }

    /**
     * 处理对手的输入包
     */
    private void handleInputPacket(InputPacket packet) {
        if (frameSyncManager == null) return;

        // 解码输入指令
        InputCommand cmd = InputCodec.decode(packet);

        // 过滤掉自己的输入（服务器会回传）
        if (cmd.playerId == localPlayerId) return;

        // 提交到帧同步管理器
        frameSyncManager.receiveRemoteInput(cmd);
    }

    /**
     * 处理匹配响应
     */
    private void handleMatchResponse(MatchResponsePacket packet) {
        switch (packet.matchStatus) {
            case MatchResponsePacket.STATUS_WAITING:
                System.out.println("[GameClient] 匹配等待中...");
                break;

            case MatchResponsePacket.STATUS_MATCHED:
                // 幂等处理：已经在游戏中则忽略重传的 MATCH_RES
                if (state == ClientState.PLAYING || state == ClientState.GAME_OVER) {
                    return;
                }
                this.opponentId = packet.opponentId;
                this.opponentPresetOrdinal = packet.opponentPresetOrdinal;
                this.opponentReady = true;
                setState(ClientState.MATCHED);

                System.out.printf("[GameClient] 匹配成功! 对手: player%d (rating=%d) preset=%s @ %s:%d\n",
                        packet.opponentId, packet.opponentRating,
                        FighterPreset.values()[packet.opponentPresetOrdinal].getDisplayName(),
                        packet.opponentAddress, packet.opponentPort);

                if (callback != null) {
                    callback.onMatchFound(packet.opponentId, packet.opponentRating);
                }
                break;

            case MatchResponsePacket.STATUS_CANCELLED:
                System.out.println("[GameClient] 匹配已取消");
                setState(ClientState.CONNECTED);
                break;

            case MatchResponsePacket.STATUS_ERROR:
                System.err.println("[GameClient] 匹配出错");
                setState(ClientState.CONNECTED);
                break;
        }
    }

    /**
     * 处理状态同步包
     */
    private void handleStatePacket(StatePacket packet) {
        // 可用于观战模式或断线重连
        System.out.printf("[GameClient] 收到状态同步: player=%.0f,%.0f health=%.0f\n",
                packet.playerX, packet.playerY, packet.health);
    }

    // ========== 结果上报 ==========

    /**
     * 上报比赛结果到服务器
     */
    public void reportResult(int winnerId) {
        ResultPacket result = new ResultPacket(nextSequence(),
                localPlayerId, opponentId, winnerId);
        udpClient.sendReliable(result);

        setState(ClientState.GAME_OVER);
        if (callback != null) callback.onGameOver(winnerId);
        System.out.printf("[GameClient] 比赛结果已上报: winner=%d\n", winnerId);
    }

    // ========== 工具方法 ==========

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

    // ========== Getters / Setters ==========

    public ClientState getState() { return state; }
    public int getLocalPlayerId() { return localPlayerId; }
    public int getOpponentId() { return opponentId; }
    public FighterPreset getPreset() { return preset; }
    public PlayerRating getPlayerRating() { return playerRating; }
    public GameWorld getGameWorld() { return gameWorld; }

    /** 渲染线程安全读取 — 获取读锁后返回 gameWorld，调用方用完须 releaseReadLock() */
    public GameWorld getGameWorldReadLocked() {
        worldLock.readLock().lock();
        return gameWorld;
    }
    public void releaseReadLock() { worldLock.readLock().unlock(); }

    /** FrameSyncManager 回滚时使用写锁 */
    public void acquireWriteLock() { worldLock.writeLock().lock(); }
    public void releaseWriteLock() { worldLock.writeLock().unlock(); }

    public FrameSyncManager getFrameSyncManager() { return frameSyncManager; }
    public UdpClient getUdpClient() { return udpClient; }

    public void setCallback(GameEventCallback callback) {
        this.callback = callback;
    }
}
