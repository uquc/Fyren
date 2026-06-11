package com.Fyren.render.libgdx;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.Fyren.GameClient;
import com.Fyren.game.FighterPreset;

/**
 * Desktop 启动器 — 解析 CLI 参数，创建 GameClient 并启动 libGDX。
 *
 * 用法:
 *   java -cp Fyren.jar com.Fyren.render.libgdx.FyrenLauncher demo --preset kage --preset2 gou
 *   java -cp Fyren.jar com.Fyren.render.libgdx.FyrenLauncher client --server <ip> --playerId <id> --preset kage
 *   java -cp Fyren.jar com.Fyren.render.libgdx.FyrenLauncher client --server <ip> --auth-server localhost --username <user> --password <pass> --preset kage
 *
 * 菜单系统（v0.2.0）：不再在 main() 中阻塞等待匹配。
 * 网络模式：创建 GameClient 并传给 FyrenGame，用户在 TitleScreen 中手动触发匹配。
 */
public class FyrenLauncher {
    public static void main(String[] args) {
        String mode = "demo";
        String serverIp = "127.0.0.1";
        int serverPort = 9876;
        int playerId = 0;
        FighterPreset preset = FighterPreset.TAKESHI;
        FighterPreset preset2 = FighterPreset.GOU;

        String authHost = null;
        int authPort = 8081;
        String username = null;
        String password = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--server":
                    if (++i >= args.length) { System.err.println("--server missing arg"); return; }
                    serverIp = args[i]; break;
                case "--port":
                    if (++i >= args.length) { System.err.println("--port missing arg"); return; }
                    serverPort = Integer.parseInt(args[i]); break;
                case "--playerId":
                    if (++i >= args.length) { System.err.println("--playerId missing arg"); return; }
                    playerId = Integer.parseInt(args[i]); break;
                case "--preset":
                    if (++i >= args.length) { System.err.println("--preset missing arg"); return; }
                    preset = FighterPreset.valueOf(args[i].toUpperCase()); break;
                case "--preset2":
                    if (++i >= args.length) { System.err.println("--preset2 missing arg"); return; }
                    preset2 = FighterPreset.valueOf(args[i].toUpperCase()); break;
                case "--auth-server":
                    if (++i >= args.length) { System.err.println("--auth-server missing arg"); return; }
                    authHost = args[i]; break;
                case "--auth-port":
                    if (++i >= args.length) { System.err.println("--auth-port missing arg"); return; }
                    authPort = Integer.parseInt(args[i]); break;
                case "--username":
                    if (++i >= args.length) { System.err.println("--username missing arg"); return; }
                    username = args[i]; break;
                case "--password":
                    if (++i >= args.length) { System.err.println("--password missing arg"); return; }
                    password = args[i]; break;
                default:
                    if (i == 0 && !args[i].startsWith("--")) mode = args[i];
                    break;
            }
        }

        GameClient gameClient = null;

        if ("client".equals(mode) || "network".equals(mode)) {
            // Authentication
            if (playerId == 0 && username != null && password != null && authHost != null) {
                System.out.println("[FyrenLauncher] Authenticating " + username + "...");
                GameClient.AuthResult auth = GameClient.login(authHost, authPort, username, password);
                if (!auth.success) {
                    System.err.println("[FyrenLauncher] Auth failed: " + auth.error);
                    return;
                }
                playerId = auth.userId;
                System.out.println("[FyrenLauncher] Auth OK! playerId=" + playerId + " mmr=" + auth.mmr);
            }

            if (playerId == 0) {
                System.err.println("[FyrenLauncher] Network mode needs --playerId or (--auth-server + --username + --password)");
                return;
            }

            // Create GameClient — connection happens later in MatchingScreen
            gameClient = new GameClient(serverIp, serverPort, playerId, preset);
            System.out.println("[FyrenLauncher] GameClient created for player " + playerId
                + " (" + preset.getDisplayName() + "), server " + serverIp + ":" + serverPort);
        }

        // Launch libGDX window — TitleScreen is the entry point
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
