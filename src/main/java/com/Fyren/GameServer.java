package com.Fyren;

import com.Fyren.auth.AuthHttpServer;
import com.Fyren.auth.AuthService;
import com.Fyren.auth.JwtTokenProvider;
import com.Fyren.auth.middleware.AuthMiddleware;
import com.Fyren.match.MatchManager;
import com.Fyren.network.*;
import com.Fyren.network.WsGameServer;
import com.Fyren.redis.RedisService;

import java.io.IOException;
import java.net.SocketException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    private WsGameServer wsGameServer;
    private final Set<Integer> wsClientIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private java.util.concurrent.ScheduledExecutorService maintenanceScheduler;
    private JwtTokenProvider jwtProvider;

    // 去重：避免双方客户端都上报 ResultPacket 导致重复统计
    private final Set<String> reportedMatches = Collections.newSetFromMap(new ConcurrentHashMap<>());

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
        this.jwtProvider = new JwtTokenProvider();
        AuthService authService = new AuthService(redisService, jwtProvider);
        AuthMiddleware authMiddleware = new AuthMiddleware(jwtProvider);

        // 启动UDP服务器
        udpServer = new UdpServer(port);

        // 启动匹配管理器
        matchManager = new MatchManager(udpServer);

        // 设置数据包处理
        udpServer.setOnPacketReceived((packet, session) -> {
            if (packet instanceof MatchRequestPacket) {
                MatchRequestPacket mrp = (MatchRequestPacket) packet;
                if (!verifyMatchAuth(mrp)) {
                    System.out.printf("[GameServer] JWT验证失败: playerId=%d\n", mrp.playerId);
                    MatchResponsePacket errResp = new MatchResponsePacket(
                            mrp.sequence, MatchResponsePacket.STATUS_ERROR, 0, 0, "", 0, 0);
                    udpServer.sendReliableTo(errResp, session.address);
                    return;
                }
                matchManager.handleMatchRequest(mrp, session);
            } else if (packet instanceof ResultPacket) {
                ResultPacket rp = (ResultPacket) packet;
                // 去重：同一场比赛双方客户端都可能上报，只处理首次
                String matchKey = Math.min(rp.player1Id, rp.player2Id) + "-" + Math.max(rp.player1Id, rp.player2Id);
                if (!reportedMatches.add(matchKey)) {
                    return; // 已处理过，跳过
                }
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
        maintenanceScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();

        // 启动 HTTP 状态 API（在 matchManager 之前初始化，以便回调引用）
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

        // 匹配生命周期回调：追踪活跃匹配数
        matchManager.setOnMatchCreated(() -> {
            if (httpStatusServer != null) httpStatusServer.incrementActiveMatches();
        });
        matchManager.setOnMatchEnded(() -> {
            if (httpStatusServer != null) httpStatusServer.decrementActiveMatches();
        });
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

        matchManager.start();

        // 启动 WebSocket 服务端（浏览器客户端，端口 9878）
        wsGameServer = new WsGameServer(9878);
        wsGameServer.setOnPacketReceived((packet, wsSession) -> {
            if (packet instanceof MatchRequestPacket) {
                MatchRequestPacket mrp = (MatchRequestPacket) packet;
                // WebSocket 客户端跳过 JWT 验证（网页 Demo 无登录流程，Guest 模式）
                // 桌面 UDP 客户端仍需 JWT 验证（见上方 UDP handler）
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

        // 定时清理超时的 WebSocket 客户端
        maintenanceScheduler.scheduleAtFixedRate(() -> {
            if (wsGameServer != null) wsGameServer.checkClientTimeouts();
        }, 5, 5, java.util.concurrent.TimeUnit.SECONDS);

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
        System.out.println("  WebSocket 端口: 9878");
        System.out.println("  认证 API 端口: 8081");
        System.out.println("  Redis: " + (redisService.isAvailable() ? "已连接" : "内存模式"));
        System.out.println("  输入 'stop' 停止服务器");
        System.out.println("====================================");
    }

    /**
     * 验证匹配请求的 JWT token。
     * @return true = 通过，false = token 无效/过期/playerId不匹配
     */
    private boolean verifyMatchAuth(MatchRequestPacket packet) {
        String token = packet.jwtToken;
        if (token == null || token.isEmpty()) {
            return false;
        }
        io.jsonwebtoken.Claims claims = jwtProvider.parseTokenSafe(token);
        if (claims == null) {
            return false;
        }
        // 校验 token 类型必须是 access（防止用 refresh token 冒充）
        String type = jwtProvider.getTokenType(claims);
        if (!"access".equals(type)) {
            return false;
        }
        int tokenUserId = jwtProvider.getUserId(claims);
        return tokenUserId == packet.playerId;
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
        if (wsGameServer != null) {
            try { wsGameServer.stop(); } catch (Exception e) { /* ignore */ }
        }
        if (maintenanceScheduler != null) maintenanceScheduler.shutdown();
        if (redisService != null) redisService.close();
        System.out.println("[GameServer] 服务器已停止");
    }

    /**
     * 上报比赛结果并更新MMR
     */
    public void reportMatch(int player1Id, int player2Id, int winnerId) {
        if (matchManager != null) {
            matchManager.reportMatchResult(player1Id, player2Id, winnerId);
            matchManager.notifyMatchEnded(); // → onMatchEnded 回调 → decrementActiveMatches
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
