package com.Fyren.render;

import com.Fyren.GameClient;
import com.Fyren.game.FighterPreset;
import com.Fyren.sync.InputCommand;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * 游戏窗口 — JFrame 960×540，组合 GamePanel + KeyInputHandler。
 * Swing Timer(~16ms/60fps) 驱动渲染和输入采样。
 */
public class SwingGameWindow extends JFrame {

    private final GameClient client;
    private final int localPlayerId;
    private final GamePanel gamePanel;
    private final KeyInputHandler keyInputHandler;
    private Timer renderTimer;

    public SwingGameWindow(GameClient client, int localPlayerId, FighterPreset preset) {
        super("Fyren - " + preset.getDisplayName());
        this.client = client;
        this.localPlayerId = localPlayerId;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        this.gamePanel = new GamePanel(client.getGameWorld(), localPlayerId);
        add(gamePanel);
        pack();
        setLocationRelativeTo(null);

        this.keyInputHandler = new KeyInputHandler(localPlayerId);
        addKeyListener(keyInputHandler);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stop();
                client.disconnect();
            }
        });
    }

    /** 启动渲染循环和输入采样 */
    public void start() {
        setVisible(true);

        renderTimer = new Timer(16, e -> {
            // 1. 采样输入
            InputCommand cmd = keyInputHandler.sample(0);
            client.setCurrentLocalInput(cmd);

            // 2. 冲刺检测
            if (keyInputHandler.consumeDashForward()) {
                var fighter = client.getGameWorld().getPlayer1();
                if (fighter != null) fighter.tryDash(1);
            }
            if (keyInputHandler.consumeDashBackward()) {
                var fighter = client.getGameWorld().getPlayer1();
                if (fighter != null) fighter.tryDash(-1);
            }

            // 3. 发送输入到对手
            client.sendInputToOpponent(cmd);

            // 4. 重绘
            gamePanel.repaint();
        });
        renderTimer.start();

        requestFocusInWindow();
    }

    public void stop() {
        if (renderTimer != null) {
            renderTimer.stop();
        }
    }

    public GamePanel getGamePanel() { return gamePanel; }
    public KeyInputHandler getKeyInputHandler() { return keyInputHandler; }
}
