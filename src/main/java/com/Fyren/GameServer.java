package com.Fyren;

import com.Fyren.auth.AuthHttpServer;
import com.Fyren.auth.AuthService;
import com.Fyren.auth.JwtTokenProvider;
import com.Fyren.auth.middleware.AuthMiddleware;
import com.Fyren.match.MatchManager;
import com.Fyren.network.*;
import com.Fyren.redis.RedisService;

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
    private RedisService redisService;
    private AuthHttpServer authHttpServer;

    public GameServer(int port) {
        this.port = port;
    }

    /**
     * 启动服务器
     */
    public void start() throws SocketException {
        // 初始化 Redis
        redisService = new RedisService();
        redisService.init();

        // 初始化 JWT + Auth
        JwtTokenProvider jwtProvider = new JwtTokenProvider();
        AuthService authService = new AuthService(redisService, jwtProvider);
        AuthMiddleware authMiddleware = new AuthMiddleware(jwtProvider);

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
                // 更新 MMR 到 Redis
                if (redisService != null && redisService.isAvailable()) {
                    try {
                        com.Fyren.match.PlayerRating r1 = matchManager.getPlayerRating(rp.player1Id);
                        com.Fyren.match.PlayerRating r2 = matchManager.getPlayerRating(rp.player2Id);
                        if (r1 != null) redisService.updateMmr(rp.player1Id, r1.getRating());
                        if (r2 != null) redisService.updateMmr(rp.player2Id, r2.getRating());
                    } catch (Exception ex) {
                        // 非关键路径，忽略
                    }
                }
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
            httpStatusServer.setOnlinePlayers(0);
            httpStatusServer.setRedisService(redisService);
            httpStatusServer.start();
        } catch (IOException e) {
            System.err.println("[GameServer] HTTP 状态服务启动失败: " + e.getMessage());
        }

        // 客户端计数变化时实时更新 HTTP 状态
        udpServer.setOnClientCountChanged(count -> {
            if (httpStatusServer != null) {
                httpStatusServer.setOnlinePlayers(count);
            }
        });

        // 启动认证 API（端口 8081）
        try {
            authHttpServer = new AuthHttpServer(8081, authService, authMiddleware);
            authHttpServer.start();
        } catch (IOException e) {
            System.err.println("[GameServer] 认证 API 启动失败: " + e.getMessage());
        }

        System.out.println("====================================");
        System.out.println("  Fyren 格斗游戏服务器已启动");
        System.out.println("  UDP 游戏端口: " + port);
        System.out.println("  HTTP 状态端口: 8080");
        System.out.println("  认证 API 端口: 8081");
        System.out.println("  Redis: " + (redisService.isAvailable() ? "已连接" : "内存模式"));
        System.out.println("  输入 'stop' 停止服务器");
        System.out.println("====================================");
    }

    /**
     * 停止服务器
     */
    public void stop() {
        System.out.println("[GameServer] 正在停止服务器...");
        if (authHttpServer != null) authHttpServer.stop();
        if (httpStatusServer != null) httpStatusServer.stop();
        if (matchManager != null) matchManager.stop();
        if (udpServer != null) udpServer.stop();
        if (redisService != null) redisService.close();
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
        boolean daemon = false;

        for (String arg : args) {
            if ("--daemon".equals(arg) || "-d".equals(arg)) {
                daemon = true;
            } else {
                try {
                    port = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    System.err.println("无效参数: " + arg);
                    System.exit(1);
                }
            }
        }

        GameServer server = new GameServer(port);
        try {
            server.start();

            // 注册 shutdown hook 确保 stop() 被调用
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

            if (daemon) {
                System.out.println("[GameServer] 守护进程模式，使用 Ctrl+C 或 taskkill 停止");
                // 无限期等待，shutdown hook 负责清理
                Thread.currentThread().join();
            } else {
                // 交互式控制台输入处理
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
            }

        } catch (SocketException e) {
            System.err.println("无法启动服务器: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            // daemon 模式被中断
        } finally {
            server.stop();
        }
    }
}
