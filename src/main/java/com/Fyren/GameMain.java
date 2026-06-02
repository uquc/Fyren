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
            case "demo":
                runDemo();
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
     * 运行本地演示模式（无需网络，单人测试游戏逻辑）
     */
    private static void runDemo() {
        System.out.println("====================================");
        System.out.println("  Fyren 演示模式 — 本地双人测试");
        System.out.println("  操作说明:");
        System.out.println("    玩家1: W/A/S/D 移动, J 拳, K 脚, U 特殊技");
        System.out.println("    玩家2: ↑/←/↓/→ 移动, 1 拳, 2 脚, 3 特殊技");
        System.out.println("    输入 'quit' 退出");
        System.out.println("====================================");

        // 直接使用GameWorld进行本地双人对战
        com.Fyren.game.GameWorld world = new com.Fyren.game.GameWorld();
        com.Fyren.sync.FrameSyncManager sync = new com.Fyren.sync.FrameSyncManager(world);

        sync.start();

        // 简单的控制台输入循环
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        int frame = 0;
        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            if ("quit".equalsIgnoreCase(input)) break;

            // 解析输入
            com.Fyren.sync.InputCommand cmd1 = new com.Fyren.sync.InputCommand(frame, 1);
            com.Fyren.sync.InputCommand cmd2 = new com.Fyren.sync.InputCommand(frame, 2);

            for (char c : input.toCharArray()) {
                switch (c) {
                    case 'w': cmd1.up = true; break;
                    case 's': cmd1.down = true; break;
                    case 'a': cmd1.left = true; break;
                    case 'd': cmd1.right = true; break;
                    case 'j': cmd1.punch = true; break;
                    case 'k': cmd1.kick = true; break;
                    case 'u': cmd1.special = true; break;
                }
            }

            sync.receiveRemoteInput(cmd1);
            sync.receiveRemoteInput(cmd2);

            frame++;
            System.out.printf("帧%d: P1(%.0f,%.0f HP=%d) P2(%.0f,%.0f HP=%d)\n",
                    frame,
                    world.getPlayer1().getX(), world.getPlayer1().getY(), world.getPlayer1().getHealth(),
                    world.getPlayer2().getX(), world.getPlayer2().getY(), world.getPlayer2().getHealth());
        }

        sync.stop();
        System.out.println("演示结束");
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

    private static int generatePlayerId() {
        return (int) (System.currentTimeMillis() % 100000);
    }

    private static void printUsage() {
        System.out.println("Fyren 2D格斗游戏");
        System.out.println("用法:");
        System.out.println("  GameMain server [port]          — 启动游戏服务器");
        System.out.println("  GameMain client <ip> [port] [id] — 启动游戏客户端");
        System.out.println("  GameMain demo                   — 本地双人演示模式");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  GameMain server 9876");
        System.out.println("  GameMain client 127.0.0.1 9876");
        System.out.println("  GameMain demo");
    }
}
