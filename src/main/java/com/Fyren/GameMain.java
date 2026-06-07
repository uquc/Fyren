package com.Fyren;

import com.Fyren.game.FighterPreset;
import com.Fyren.render.SwingGameWindow;

import java.net.SocketException;

/**
 * Fyren 2D格斗游戏 — 主入口
 *
 * 启动方式：
 *   服务器模式:  java -cp Fyren.jar com.Fyren.GameMain server [port]
 *   客户端模式:  java -cp Fyren.jar com.Fyren.GameMain client <serverIp> [serverPort] [playerId]
 *
 * 默认值：
 *   serverPort = 9876
 *   playerId   = 自动生成（基于时间戳）
 *
 * 核心特性：
 *   - UDP网络通信（可靠+不可靠双通道）
 *   - 帧同步（乐观帧锁定 + GGPO式回滚）
 *   - 隐藏分MMR匹配（ELO算法 + 扩散窗口）
 */
public class GameMain {

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String mode = args[0].toLowerCase();

        switch (mode) {
            case "server":
                startServer(args);
                break;
            case "client":
                startClient(args);
                break;
            case "register":
                runRegister(args);
                break;
            case "login":
                runLogin(args);
                break;
            case "demo":
                runDemo(args);
                break;
            case "libgdx-demo":
                runLibgdxDemo(args);
                break;
            default:
                System.err.println("未知模式: " + mode);
                printUsage();
        }
    }

    /**
     * 启动服务器
     */
    private static void startServer(String[] args) {
        int port = GameServer.DEFAULT_PORT;
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("无效端口号，使用默认端口: " + GameServer.DEFAULT_PORT);
            }
        }

        // 委托给GameServer的main方法
        GameServer.main(new String[]{String.valueOf(port)});
    }

    /**
     * 启动客户端
     */
    private static void startClient(String[] args) {
        if (args.length < 2) {
            System.err.println("客户端模式需要指定服务器IP");
            System.err.println("用法: GameMain client <serverIp> [serverPort] [playerId]");
            return;
        }

        String serverHost = args[1];
        int serverPort = GameServer.DEFAULT_PORT;
        int pid = generatePlayerId();

        if (args.length > 2) {
            try {
                serverPort = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.err.println("无效端口号，使用默认端口: " + GameServer.DEFAULT_PORT);
            }
        }
        if (args.length > 3) {
            try {
                pid = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                System.err.println("无效玩家ID，使用自动生成: " + pid);
            }
        }

        final int playerId = pid;

        // 解析 --preset 参数
        FighterPreset preset = FighterPreset.TAKESHI;
        for (int i = 1; i < args.length; i++) {
            if ("--preset".equals(args[i]) && i + 1 < args.length) {
                String p = args[i + 1].toLowerCase();
                switch (p) {
                    case "kage": preset = FighterPreset.KAGE; break;
                    case "takeshi": preset = FighterPreset.TAKESHI; break;
                    case "gou": preset = FighterPreset.GOU; break;
                    default:
                        System.err.println("未知preset: " + p + "，使用默认takeshi");
                }
            }
        }
        final FighterPreset selectedPreset = preset;

        System.out.println("====================================");
        System.out.println("  Fyren 格斗游戏客户端");
        System.out.println("  服务器: " + serverHost + ":" + serverPort);
        System.out.println("  玩家ID: " + playerId);
        System.out.println("  角色: " + preset.getDisplayName());
        System.out.println("====================================");

        GameClient client = new GameClient(serverHost, serverPort, playerId, selectedPreset);
        client.setCallback(new GameClient.GameEventCallback() {
            @Override
            public void onStateChanged(GameClient.ClientState newState) {
                System.out.println("[状态] " + newState);
            }

            @Override
            public void onMatchFound(int opponentId, int opponentRating) {
                System.out.println(">>> 找到对手! 对手ID=" + opponentId + ", MMR=" + opponentRating);
                client.startGame();

                // 创建渲染窗口
                SwingGameWindow window = new SwingGameWindow(client, playerId, selectedPreset);
                window.start();
            }

            @Override
            public void onGameStart() {
                System.out.println(">>> 游戏开始! 可以开始操作");
                System.out.println("    操作说明: W/S/A/D 移动, J 拳, K 脚, U 特殊技");
            }

            @Override
            public void onGameOver(int winnerId) {
                if (winnerId == playerId) {
                    System.out.println(">>> 你赢了!");
                } else if (winnerId == -1) {
                    System.out.println(">>> 平局!");
                } else {
                    System.out.println(">>> 你输了! 胜者: player" + winnerId);
                }
            }

            @Override
            public void onError(Exception e) {
                System.err.println("[错误] " + e.getMessage());
            }
        });

        try {
            // 连接服务器
            client.connect();

            // 启动匹配
            client.requestMatch();

            // SwingGameWindow 处理输入和渲染，主线程保持存活
            while (client.getState() == GameClient.ClientState.MATCHING ||
                   client.getState() == GameClient.ClientState.PLAYING) {
                try { Thread.sleep(100); } catch (InterruptedException e) { break; }
            }

        } catch (SocketException e) {
            System.err.println("无法连接服务器: " + e.getMessage());
        } finally {
            client.disconnect();
        }
    }

    /**
     * 运行本地演示模式（Swing渲染，双人对战）
     */
    private static void runDemo(String[] args) {
        FighterPreset p1Preset = parsePresetArg(args, "--preset", FighterPreset.TAKESHI);
        FighterPreset p2Preset = parsePresetArg(args, "--preset2", FighterPreset.GOU);

        System.out.println("====================================");
        System.out.println("  Fyren 演示模式 — 本地双人对战");
        System.out.println("  P1(" + p1Preset.getDisplayName() + "): W/A/S/D 移动, J 拳, K 脚, U 特殊技");
        System.out.println("  P2(" + p2Preset.getDisplayName() + "): ↑/←/↓/→ 移动, 1 拳, 2 脚, 3 特殊技");
        System.out.println("====================================");

        com.Fyren.render.DemoGameWindow window = new com.Fyren.render.DemoGameWindow(p1Preset, p2Preset);
        window.start();

        // 阻塞直到游戏结束
        while (!window.getGameWorld().isGameOver()) {
            try { Thread.sleep(500); } catch (InterruptedException e) { break; }
        }

        // 显示结果
        int winner = window.getGameWorld().getWinnerId();
        if (winner == 0) {
            System.out.println(">>> 平局!");
        } else {
            System.out.println(">>> P" + winner + " 获胜!");
        }
        System.out.println("演示结束 — 关闭窗口退出");
    }

    /** 从命令行参数解析preset */
    private static FighterPreset parsePresetArg(String[] args, String flag, FighterPreset defaultPreset) {
        for (int i = 0; i < args.length - 1; i++) {
            if (flag.equals(args[i])) {
                switch (args[i + 1].toLowerCase()) {
                    case "kage": return FighterPreset.KAGE;
                    case "takeshi": return FighterPreset.TAKESHI;
                    case "gou": return FighterPreset.GOU;
                }
            }
        }
        return defaultPreset;
    }

    /**
     * 控制台输入循环（模拟真实键盘输入）
     * 实际项目中应替换为AWT/Swing或LWJGL的键盘事件监听
     */
    private static void startConsoleInputLoop(GameClient client, int playerId) {
        System.out.println("\n输入操作指令 (W/A/S/D移动, J拳, K脚, U特殊技, Q退出):");

        java.util.Scanner scanner = new java.util.Scanner(System.in);
        while (client.getState() == GameClient.ClientState.MATCHING ||
               client.getState() == GameClient.ClientState.PLAYING) {

            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;
            if ("q".equalsIgnoreCase(input)) break;

            boolean up = false, down = false, left = false, right = false;
            boolean punch = false, kick = false, special = false;

            for (char c : input.toLowerCase().toCharArray()) {
                switch (c) {
                    case 'w': up = true; break;
                    case 's': down = true; break;
                    case 'a': left = true; break;
                    case 'd': right = true; break;
                    case 'j': punch = true; break;
                    case 'k': kick = true; break;
                    case 'u': special = true; break;
                }
            }

            client.submitInput(up, down, left, right, punch, kick, special);
        }
    }

    /** libGDX 本地双人演示模式 */
    private static void runLibgdxDemo(String[] args) {
        FighterPreset p1Preset = parsePresetArg(args, "--preset", FighterPreset.KAGE);
        FighterPreset p2Preset = parsePresetArg(args, "--preset2", FighterPreset.GOU);

        System.out.println("====================================");
        System.out.println("  Fyren libGDX 演示模式");
        System.out.println("  P1(" + p1Preset.getDisplayName() + "): WASD 移动, J 拳, K 脚, U 特殊技");
        System.out.println("  P2(" + p2Preset.getDisplayName() + "): ↑←↓→ 移动, 1 拳, 2 脚, 3 特殊技");
        System.out.println("====================================");

        com.Fyren.render.libgdx.FyrenLauncher.main(new String[]{
            "--mode", "demo", "--preset", p1Preset.name(), "--preset2", p2Preset.name()
        });
    }

    private static int generatePlayerId() {
        return (int) (System.currentTimeMillis() % 100000);
    }

    /** 注册新用户 */
    private static void runRegister(String[] args) {
        if (args.length < 4) {
            System.err.println("用法: GameMain register <authHost> <username> <password> [authPort]");
            System.err.println("示例: GameMain register localhost testuser 123456");
            return;
        }

        String authHost = args[1];
        String username = args[2];
        String password = args[3];
        int authPort = 8081;
        if (args.length > 4) {
            try { authPort = Integer.parseInt(args[4]); } catch (NumberFormatException e) {}
        }

        System.out.println("正在注册...");
        GameClient.AuthResult result = GameClient.register(authHost, authPort, username, password);
        if (result.success) {
            System.out.println("注册成功!");
            System.out.println("  userId: " + result.userId);
            System.out.println("  username: " + result.username);
            System.out.println("  MMR: " + result.mmr);
        } else {
            System.err.println("注册失败: " + result.error);
        }
    }

    /** 登录并启动客户端 */
    private static void runLogin(String[] args) {
        if (args.length < 4) {
            System.err.println("用法: GameMain login <authHost> <username> <password> [authPort] [--preset kage|takeshi|gou]");
            System.err.println("示例: GameMain login localhost testuser 123456 --preset kage");
            return;
        }

        String authHost = args[1];
        String username = args[2];
        String password = args[3];
        int authPort = 8081;
        if (args.length > 4 && !args[4].startsWith("--")) {
            try { authPort = Integer.parseInt(args[4]); } catch (NumberFormatException e) {}
        }

        // 解析 preset
        FighterPreset preset = FighterPreset.TAKESHI;
        for (int i = 1; i < args.length; i++) {
            if ("--preset".equals(args[i]) && i + 1 < args.length) {
                switch (args[i + 1].toLowerCase()) {
                    case "kage": preset = FighterPreset.KAGE; break;
                    case "takeshi": preset = FighterPreset.TAKESHI; break;
                    case "gou": preset = FighterPreset.GOU; break;
                }
            }
        }

        System.out.println("正在登录 " + authHost + ":" + authPort + " ...");
        GameClient.AuthResult result = GameClient.login(authHost, authPort, username, password);
        if (!result.success) {
            System.err.println("登录失败: " + result.error);
            return;
        }

        System.out.println("登录成功! userId=" + result.userId + ", username=" + result.username + ", mmr=" + result.mmr);
        System.out.println("角色: " + preset.getDisplayName());

        // 使用认证返回的 userId 连接游戏服务器
        String gameHost = args[1]; // 游戏服务器与认证服务器同主机
        int gamePort = 9876;

        final int playerId = result.userId;
        final FighterPreset selectedPreset = preset;

        System.out.println("====================================");
        System.out.println("  Fyren 格斗游戏客户端");
        System.out.println("  认证用户: " + result.username + " (ID=" + playerId + ")");
        System.out.println("  游戏服务器: " + gameHost + ":" + gamePort);
        System.out.println("  角色: " + preset.getDisplayName());
        System.out.println("====================================");

        GameClient client = new GameClient(gameHost, gamePort, playerId, result.mmr, selectedPreset);
        client.setTokens(result.accessToken, result.refreshToken);
        client.setCallback(new GameClient.GameEventCallback() {
            @Override
            public void onStateChanged(GameClient.ClientState newState) {
                System.out.println("[状态] " + newState);
            }

            @Override
            public void onMatchFound(int opponentId, int opponentRating) {
                System.out.println(">>> 找到对手! 对手ID=" + opponentId + ", MMR=" + opponentRating);
                client.startGame();
                com.Fyren.render.SwingGameWindow window = new com.Fyren.render.SwingGameWindow(client, playerId, selectedPreset);
                window.start();
            }

            @Override
            public void onGameStart() {
                System.out.println(">>> 游戏开始!");
            }

            @Override
            public void onGameOver(int winnerId) {
                if (winnerId == playerId) {
                    System.out.println(">>> 你赢了!");
                } else if (winnerId == -1) {
                    System.out.println(">>> 平局!");
                } else {
                    System.out.println(">>> 你输了! 胜者: player" + winnerId);
                }
            }

            @Override
            public void onError(Exception e) {
                System.err.println("[错误] " + e.getMessage());
            }
        });

        try {
            client.connect();
            client.requestMatch();

            while (client.getState() == GameClient.ClientState.MATCHING ||
                   client.getState() == GameClient.ClientState.PLAYING) {
                try { Thread.sleep(100); } catch (InterruptedException e) { break; }
            }
        } catch (SocketException e) {
            System.err.println("无法连接游戏服务器: " + e.getMessage());
        } finally {
            client.disconnect();
        }
    }

    private static void printUsage() {
        System.out.println("Fyren 2D格斗游戏");
        System.out.println("用法:");
        System.out.println("  GameMain server [port]                   — 启动游戏服务器");
        System.out.println("  GameMain client <ip> [port] [id]         — 启动游戏客户端（直连）");
        System.out.println("  GameMain register <host> <user> <pass>   — 注册新用户");
        System.out.println("  GameMain login <host> <user> <pass>      — 登录并进入匹配");
        System.out.println("  GameMain demo                            — 本地双人演示模式 (Swing)");
        System.out.println("  GameMain libgdx-demo                     — 本地双人演示模式 (libGDX)");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  GameMain server 9876");
        System.out.println("  GameMain register localhost kage_user 123456");
        System.out.println("  GameMain login localhost kage_user 123456 --preset kage");
        System.out.println("  GameMain demo");
    }
}
