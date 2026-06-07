package com.Fyren.render;

import com.Fyren.game.Fighter;
import com.Fyren.game.GameWorld;

import javax.swing.*;
import java.awt.*;

/**
 * 游戏渲染面板 — 从 GameWorld 读取只读状态，委托 StickFigureRenderer 绘制。
 */
public class GamePanel extends JPanel {

    private GameWorld gameWorld;
    private int localPlayerId;

    public static final int PANEL_WIDTH = 960;
    public static final int PANEL_HEIGHT = 540;
    private static final int GROUND_Y = 400;
    private static final int HEALTH_BAR_WIDTH = 300;
    private static final int HEALTH_BAR_HEIGHT = 20;
    private static final int HEALTH_BAR_Y = 60;
    private static final int MARGIN_X = 80;          // 屏幕边缘留白
    private static final float MIN_SCREEN_DIST = 150f; // 两人最近屏幕距离
    private static final float MAX_SCREEN_DIST = 680f; // 两人最远屏幕距离

    public GamePanel(GameWorld gameWorld, int localPlayerId) {
        this.gameWorld = gameWorld;
        this.localPlayerId = localPlayerId;
        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        setBackground(Color.BLACK);
        setDoubleBuffered(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Fighter p1 = gameWorld.getPlayer1();
        Fighter p2 = gameWorld.getPlayer2();

        // 地面线
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawLine(0, GROUND_Y, PANEL_WIDTH, GROUND_Y);

        // 世界坐标 → 屏幕坐标映射
        float worldCenter = (p1.getX() + p2.getX()) / 2f;
        float worldDist = Math.abs(p2.getX() - p1.getX());

        // 屏幕距离：随世界距离缩放，但限制在合理范围
        float scale = 1.0f;
        if (worldDist > 500) scale = MAX_SCREEN_DIST / worldDist;
        else if (worldDist < 200) scale = MIN_SCREEN_DIST / worldDist;

        float screenCenter = PANEL_WIDTH / 2f;
        float p1ScreenX = screenCenter + (p1.getX() - worldCenter) * scale;
        float p2ScreenX = screenCenter + (p2.getX() - worldCenter) * scale;

        StickFigureRenderer.drawFighter(g2d, p1, p1ScreenX, GROUND_Y);
        StickFigureRenderer.drawFighter(g2d, p2, p2ScreenX, GROUND_Y);

        // 攻击框调试可视化（屏幕坐标直接基于 Fighter 的世界偏移映射）
        if (p1.isAttacking()) {
            Rectangle ab = p1.getAttackBox();
            if (ab.width > 0) {
                float abScreenX = p1ScreenX + (p1.isFacingRight() ? p1.getPreset().getHitboxWidth() / 2f : -p1.getPreset().getHitboxWidth() / 2f - ab.width);
                StickFigureRenderer.drawAttackBox(g2d, new Rectangle((int)abScreenX, GROUND_Y - ab.height, ab.width, ab.height));
            }
        }
        if (p2.isAttacking()) {
            Rectangle ab = p2.getAttackBox();
            if (ab.width > 0) {
                float abScreenX = p2ScreenX + (p2.isFacingRight() ? p2.getPreset().getHitboxWidth() / 2f : -p2.getPreset().getHitboxWidth() / 2f - ab.width);
                StickFigureRenderer.drawAttackBox(g2d, new Rectangle((int)abScreenX, GROUND_Y - ab.height, ab.width, ab.height));
            }
        }

        // 血量条
        StickFigureRenderer.drawHealthBar(g2d, 20, HEALTH_BAR_Y,
                HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT,
                p1.getHealth(), p1.getMaxHealth(), p1.getPreset().getLineColor(), true);
        StickFigureRenderer.drawHealthBar(g2d, PANEL_WIDTH - 20 - HEALTH_BAR_WIDTH, HEALTH_BAR_Y,
                HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT,
                p2.getHealth(), p2.getMaxHealth(), p2.getPreset().getLineColor(), false);

        // 角色名
        g2d.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2d.setColor(Color.WHITE);
        g2d.drawString(p1.getPreset().getDisplayName(), 22, HEALTH_BAR_Y - 5);
        String p2Name = p2.getPreset().getDisplayName();
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(p2Name, PANEL_WIDTH - 22 - fm.stringWidth(p2Name), HEALTH_BAR_Y - 5);

        // 特殊技状态
        drawSpecialIndicator(g2d, p1, 22, HEALTH_BAR_Y + 35);
        drawSpecialIndicator(g2d, p2, PANEL_WIDTH - 22 - 150, HEALTH_BAR_Y + 35);

        // 冲刺次数
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.drawString("Dash: " + p1.getDashCharges(), 22, HEALTH_BAR_Y + 55);
        g2d.drawString("Dash: " + p2.getDashCharges(), PANEL_WIDTH - 22 - 60, HEALTH_BAR_Y + 55);

        // 倒计时
        StickFigureRenderer.drawTimer(g2d, gameWorld.getTimerSeconds(), PANEL_WIDTH);

        // 游戏结束
        if (gameWorld.isGameOver()) {
            drawGameOverOverlay(g2d);
        }
    }

    private void drawSpecialIndicator(Graphics2D g, Fighter f, int x, int y) {
        String text;
        switch (f.getPreset()) {
            case KAGE:
                if (f.getSpecialCooldownRemaining() > 0) {
                    text = String.format("SP CD: %.1fs", f.getSpecialCooldownRemaining() / 60.0f);
                } else {
                    text = "SP READY";
                }
                break;
            case TAKESHI:
                text = String.format("SP: %d/40 dmg", f.getDamageDealtSinceLastSpecial());
                break;
            case GOU:
                text = String.format("SP: %d/50 taken", f.getDamageTakenSinceLastSpecial());
                break;
            default:
                text = "";
        }
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(f.isSpecialReady() ? Color.GREEN : Color.GRAY);
        g.drawString(text, x, y);
    }

    private void drawGameOverOverlay(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
        g.setFont(new Font("SansSerif", Font.BOLD, 48));
        g.setColor(Color.WHITE);

        int winner = gameWorld.getWinnerId();
        String text;
        if (winner == 0) {
            text = "DRAW!";
        } else if (localPlayerId <= 0) {
            // 中立/观众模式（如 Demo 双人），显示具体胜者
            text = "P" + winner + " WINS!";
        } else if (winner == localPlayerId) {
            text = "YOU WIN!";
        } else {
            text = "YOU LOSE!";
        }
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (PANEL_WIDTH - fm.stringWidth(text)) / 2, PANEL_HEIGHT / 2);
    }

    public void setGameWorld(GameWorld gameWorld) { this.gameWorld = gameWorld; }
}
