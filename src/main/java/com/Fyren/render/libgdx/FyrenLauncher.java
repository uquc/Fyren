package com.Fyren.render.libgdx;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.Fyren.GameClient;
import com.Fyren.game.FighterPreset;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Desktop 启动器 — 解析命令行参数，创建 GameClient 并启动 libGDX。
 *
 * 用法:
 *   # Demo 本地双人
 *   java -cp Fyren.jar com.Fyren.render.libgdx.FyrenLauncher demo --preset kage --preset2 gou
 *
 *   # 网络对战（已知 playerId）
 *   java -cp Fyren.jar com.Fyren.render.libgdx.FyrenLauncher client --server <ip> --playerId <id> --preset kage
 *
 *   # 网络对战（自动认证）
 *   java -cp Fyren.jar com.Fyren.render.libgdx.FyrenLauncher client --server <ip> --auth-server localhost --username <user> --password <pass> --preset kage
 */
public class FyrenLauncher {
    public static void main(String[] args) {
        String mode = "demo";
        String serverIp = "127.0.0.1";
        int serverPort = 9876;
        int playerId = 0;
        FighterPreset preset = FighterPreset.TAKESHI;
        FighterPreset preset2 = FighterPreset.GOU;

        // 认证参数
        String authHost = null;
        int authPort = 8081;
        String username = null;
        String password = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--server":
                    if (++i >= args.length) { System.err.println("--server 缺少参数"); return; }
                    serverIp = args[i]; break;
                case "--port":
                    if (++i >= args.length) { System.err.println("--port 缺少参数"); return; }
                    serverPort = Integer.parseInt(args[i]); break;
                case "--playerId":
                    if (++i >= args.length) { System.err.println("--playerId 缺少参数"); return; }
                    playerId = Integer.parseInt(args[i]); break;
                case "--preset":
                    if (++i >= args.length) { System.err.println("--preset 缺少参数"); return; }
                    preset = FighterPreset.valueOf(args[i].toUpperCase()); break;
                case "--preset2":
                    if (++i >= args.length) { System.err.println("--preset2 缺少参数"); return; }
                    preset2 = FighterPreset.valueOf(args[i].toUpperCase()); break;
                case "--auth-server":
                    if (++i >= args.length) { System.err.println("--auth-server 缺少参数"); return; }
                    authHost = args[i]; break;
                case "--auth-port":
                    if (++i >= args.length) { System.err.println("--auth-port 缺少参数"); return; }
                    authPort = Integer.parseInt(args[i]); break;
                case "--username":
                    if (++i >= args.length) { System.err.println("--username 缺少参数"); return; }
                    username = args[i]; break;
                case "--password":
                    if (++i >= args.length) { System.err.println("--password 缺少参数"); return; }
                    password = args[i]; break;
                default:
                    // 第一个非选项参数视为 mode
                    if (i == 0 && !args[i].startsWith("--")) {
                        mode = args[i];
                    }
                    break;
            }
        }

        GameClient gameClient = null;

        if ("client".equals(mode) || "network".equals(mode)) {
            // === 认证 ===
            if (playerId == 0 && username != null && password != null && authHost != null) {
                System.out.println("[FyrenLauncher] 正在认证 " + username + "...");
                GameClient.AuthResult auth = GameClient.login(authHost, authPort, username, password);
                if (!auth.success) {
                    System.err.println("[FyrenLauncher] 认证失败: " + auth.error);
                    return;
                }
                playerId = auth.userId;
                System.out.println("[FyrenLauncher] 认证成功! playerId=" + playerId + " mmr=" + auth.mmr);
            }

            if (playerId == 0) {
                System.err.println("[FyrenLauncher] 网络模式需要 --playerId 或 (--auth-server + --username + --password)");
                return;
            }

            // === 连接服务器 + 匹配 ===
            try {
                System.out.println("[FyrenLauncher] 正在连接 " + serverIp + ":" + serverPort + "...");
                gameClient = new GameClient(serverIp, serverPort, playerId, preset);
                gameClient.connect();

                System.out.println("[FyrenLauncher] 正在请求匹配...");
                gameClient.requestMatch();

                // 等待匹配结果
                CountDownLatch matchLatch = new CountDownLatch(1);
                gameClient.setCallback(new GameClient.GameEventCallback() {
                    @Override
                    public void onStateChanged(GameClient.ClientState newState) {}

                    @Override
                    public void onMatchFound(int opponentId, int opponentRating) {
                        System.out.println("[FyrenLauncher] 匹配成功! 对手: player" + opponentId
                            + " (rating=" + opponentRating + ")");
                        matchLatch.countDown();
                    }

                    @Override
                    public void onGameStart() {}

                    @Override
                    public void onGameOver(int winnerId) {}

                    @Override
                    public void onError(Exception e) {
                        System.err.println("[FyrenLauncher] 匹配出错: " + e.getMessage());
                        matchLatch.countDown();
                    }
                });

                boolean matched = matchLatch.await(60, TimeUnit.SECONDS);
                if (!matched || gameClient.getState() != GameClient.ClientState.MATCHED) {
                    System.err.println("[FyrenLauncher] 匹配超时或失败");
                    gameClient.disconnect();
                    return;
                }
            } catch (Exception e) {
                System.err.println("[FyrenLauncher] 连接失败: " + e.getMessage());
                e.printStackTrace();
                return;
            }
        }

        // === 启动 libGDX 窗口 ===
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Fyren");
        config.setWindowedMode(960, 540);
        config.useVsync(true);
        config.setForegroundFPS(60);

        final GameClient clientRef = gameClient;
        FyrenGame game = "demo".equals(mode)
            ? FyrenGame.createDemo(preset, preset2)
            : FyrenGame.createNetworkClient(clientRef);

        new Lwjgl3Application(game, config);
    }
}
