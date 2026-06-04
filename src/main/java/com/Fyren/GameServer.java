package com.Fyren;

import com.Fyren.match.MatchManager;
import com.Fyren.network.*;

import java.io.IOException;
import java.net.SocketException;

/**
 * 游戏服务器 — 启动UDP服务器和匹配管理器
 *
 * 职责：
 * 1. 监听UDP端口，接受客户端连接
 * 2. 处理匹配请求，委托给MatchManager
 * 3. 中转游戏数据（当P2P直连不可用时）
 * 4. 管理活跃游戏会话
 *
 * 启动方式：
 *   java -cp Fyren.jar com.Fyren.GameServer [port]
 *
 * 默认端口: 9876
 */
public class GameServer {
    public static final int DEFAULT_PORT = 9876;

    private final int port;
    private UdpServer udpServer;
    private MatchManager matchManager;
    private HttpStatusServer httpStatusServer;

    public GameServer(int port) {
        this.port = port;
    }

    /**
     * 启动服务器
     */
    public void start() throws SocketException {
        // 启动UDP服务器
        udpServer = new UdpServer(port);

        // 启动匹配管理器
        matchManager = new MatchManager(udpServer);

        // 设置数据包处理
        udpServer.setOnPacketReceived((packet, session) -> {
            if (packet instanceof MatchRequestPacket) {
                matchManager.handleMatchRequest((MatchRequestPacket) packet, session);
            } else if (packet instanceof ResultPacket) {
                ResultPacket rp = (ResultPacket) packet;
                System.out.printf("[GameServer] 收到比赛结果: P%d vs P%d, 胜者=%d\n",
                        rp.player1Id, rp.player2Id, rp.winnerId);
                reportMatch(rp.player1Id, rp.player2Id, rp.winnerId);
            }
            // 其他类型的包在UdpServer内部处理（INPUT转发、HEARTBEAT等）
        });

        udpServer.setOnError(e -> {
            System.err.println("[GameServer] 服务器错误: " + e.getMessage());
        });

        udpServer.start();
        matchManager.start();

        // 启动 HTTP 状态 API
        try {
            httpStatusServer = new HttpStatusServer(8080);
            httpStatusServer.setOnlinePlayers(udpServer.getClients().size());
            httpStatusServer.start();
        } catch (IOException e) {
            System.err.println("[GameServer] HTTP 状态服务启动失败: " + e.getMessage());
        }

        System.out.println("====================================");
        System.out.println("  Fyren 格斗游戏服务器已启动");
        System.out.println("  UDP 游戏端口: " + port);
        System.out.println("  HTTP 状态端口: 8080");
        System.out.println("  输入 'stop' 停止服务器");
        System.out.println("====================================");
    }

    /**
     * 停止服务器
     */
    public void stop() {
        System.out.println("[GameServer] 正在停止服务器...");
        if (httpStatusServer != null) httpStatusServer.stop();
        if (matchManager != null) matchManager.stop();
        if (udpServer != null) udpServer.stop();
        System.out.println("[GameServer] 服务器已停止");
    }

    /**
     * 上报比赛结果并更新MMR
     */
    public void reportMatch(int player1Id, int player2Id, int winnerId) {
        if (matchManager != null) {
            matchManager.reportMatchResult(player1Id, player2Id, winnerId);
        }
        if (httpStatusServer != null) {
            httpStatusServer.incrementMatches();
        }
    }

    // ========== Getters ==========
    public UdpServer getUdpServer() { return udpServer; }
    public MatchManager getMatchManager() { return matchManager; }
    public int getPort() { return port; }

    // ========== 入口 ==========

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("无效的端口号: " + args[0]);
                System.exit(1);
            }
        }

        GameServer server = new GameServer(port);
        try {
            server.start();

            // 简单的控制台输入处理
            java.util.Scanner scanner = new java.util.Scanner(System.in);
            while (true) {
                String cmd = scanner.nextLine().trim();
                if ("stop".equalsIgnoreCase(cmd) || "quit".equalsIgnoreCase(cmd)) {
                    break;
                } else if ("status".equalsIgnoreCase(cmd)) {
                    System.out.println("活跃客户端: " + server.getUdpServer().getClients().size());
                    System.out.println("匹配队列: " + server.getMatchManager().getMatchmaker().getQueueSize());
                    System.out.println("活跃会话: " + server.getUdpServer().getGameSessions().size());
                } else if (cmd.startsWith("mmr ")) {
                    try {
                        int playerId = Integer.parseInt(cmd.substring(4));
                        com.Fyren.match.PlayerRating rating = server.getMatchManager().getPlayerRating(playerId);
                        if (rating != null) {
                            System.out.println(rating);
                        } else {
                            System.out.println("未找到玩家" + playerId);
                        }
                    } catch (Exception e) {
                        System.out.println("用法: mmr <playerId>");
                    }
                }
            }

        } catch (SocketException e) {
            System.err.println("无法启动服务器: " + e.getMessage());
            e.printStackTrace();
        } finally {
            server.stop();
        }
    }
}
