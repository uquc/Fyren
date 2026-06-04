package com.Fyren.render;

import com.Fyren.game.FighterPreset;
import com.Fyren.game.GameWorld;
import com.Fyren.sync.InputCommand;

import javax.swing.*;
import java.util.Arrays;

/**
 * 本地双人演示窗口 — Swing Timer 直接驱动 GameWorld，无需网络/帧同步。
 * P1: WASD+JKU, P2: 方向键+123
 */
public class DemoGameWindow extends JFrame {

    private final GameWorld gameWorld;
    private final GamePanel gamePanel;
    private final KeyInputHandler p1Input;
    private final KeyInputHandler p2Input;
    private Timer gameTimer;
    private int localFrame = 0;

    public DemoGameWindow(FighterPreset p1Preset, FighterPreset p2Preset) {
        super("Fyren Demo — " + p1Preset.getDisplayName() + " vs " + p2Preset.getDisplayName());

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        // 初始化游戏世界
        this.gameWorld = new GameWorld();
        gameWorld.setupPlayers(p1Preset, p2Preset);

        // 渲染面板
        this.gamePanel = new GamePanel(gameWorld, 1);
        add(gamePanel);
        pack();
        setLocationRelativeTo(null);

        // 双人输入
        this.p1Input = new KeyInputHandler(1);
        this.p2Input = KeyInputHandler.forPlayer2();
        addKeyListener(p1Input);
        addKeyListener(p2Input);
    }

    /** 启动渲染和游戏循环 */
    public void start() {
        setVisible(true);

        gameTimer = new Timer(16, e -> {
            if (gameWorld.isGameOver()) {
                gameTimer.stop();
                return;
            }

            localFrame++;

            // 采样双方输入（含冲刺标志）
            InputCommand cmd1 = p1Input.sample(localFrame);
            InputCommand cmd2 = p2Input.sample(localFrame);

            // 直接推进游戏逻辑
            gameWorld.update(Arrays.asList(cmd1, cmd2), localFrame);

            // 重绘
            gamePanel.repaint();
        });
        gameTimer.start();

        requestFocusInWindow();
    }

    public GameWorld getGameWorld() { return gameWorld; }
    public GamePanel getGamePanel() { return gamePanel; }
}
