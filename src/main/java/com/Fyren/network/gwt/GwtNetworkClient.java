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

                System.out.println("[GwtClient] 匹配成功! 对手: player" + packet.opponentId
                        + " (rating=" + packet.opponentRating + ") preset="
                        + FighterPreset.values()[packet.opponentPresetOrdinal].getDisplayName());

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
        System.out.println("[GwtClient] 比赛结果已上报: winner=" + winnerId);
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
