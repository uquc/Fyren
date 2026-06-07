package com.Fyren.render;

import com.Fyren.game.Fighter;
import com.Fyren.game.FighterPreset;
import com.Fyren.game.FighterStance;

import java.awt.*;
import java.awt.geom.Line2D;

/**
 * 火柴人渲染器 — 纯静态工具方法。
 * 根据 Fighter 属性 + FighterPreset + FighterStance 绘制火柴人。
 */
public class StickFigureRenderer {

    private static final float HEAD_RADIUS = 10f;

    /** Convert packed RGBA int (0xAARRGGBB) to java.awt.Color */
    public static Color toColor(int packed) {
        return new Color((packed >> 16) & 0xFF, (packed >> 8) & 0xFF, packed & 0xFF,
                         (packed >> 24) & 0xFF);
    }
    private static final float BODY_LENGTH = 30f;
    private static final float LIMB_LENGTH = 20f;

    /**
     * 主绘制入口
     * @param g2d     Graphics2D
     * @param fighter 角色
     * @param x       屏幕坐标X（脚底中心）
     * @param y       屏幕坐标Y（脚底）
     */
    public static void drawFighter(Graphics2D g2d, Fighter fighter, float x, float y) {
        FighterPreset preset = fighter.getPreset();
        FighterStance stance = fighter.getStance();
        boolean facingRight = fighter.isFacingRight();

        g2d.setStroke(new BasicStroke(preset.getLineWidth(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.setColor(toColor(preset.getLineColor()));

        // 僵直红色闪烁
        if (stance == FighterStance.HURT && (System.currentTimeMillis() / 100) % 2 == 0) {
            g2d.setColor(Color.RED);
        }

        float dir = facingRight ? 1f : -1f;
        float headY = y - preset.getHitboxHeight() + HEAD_RADIUS;
        float neckY = headY + HEAD_RADIUS;
        float hipY = neckY + BODY_LENGTH;

        // 头部
        g2d.drawOval((int)(x - HEAD_RADIUS), (int)(headY - HEAD_RADIUS),
                (int)(HEAD_RADIUS * 2), (int)(HEAD_RADIUS * 2));

        // 身体倾斜
        float bodyLean = 0f;
        if (stance == FighterStance.WALK_FORWARD) bodyLean = 5f * dir;
        if (stance == FighterStance.WALK_BACKWARD) bodyLean = -3f * dir;
        if (stance == FighterStance.DASH) bodyLean = 15f * dir;
        if (stance == FighterStance.HURT) bodyLean = -8f * dir;

        g2d.draw(new Line2D.Float(x, neckY, x + bodyLean, hipY));

        // 手臂
        float shoulderX = x + bodyLean * 0.3f;
        float shoulderY = neckY + BODY_LENGTH * 0.2f;

        switch (stance) {
            case PUNCH:
                drawArm(g2d, shoulderX, shoulderY, dir, false);
                float punchX = shoulderX + dir * 35f;
                g2d.draw(new Line2D.Float(shoulderX, shoulderY, punchX, shoulderY - 2f));
                g2d.fillOval((int)(punchX - 4), (int)(shoulderY - 6), 8, 8);
                break;
            case THROW:
                float throwX = shoulderX + dir * 25f;
                g2d.draw(new Line2D.Float(shoulderX, shoulderY - 3f, throwX, shoulderY - 8f));
                g2d.draw(new Line2D.Float(shoulderX, shoulderY + 3f, throwX, shoulderY + 8f));
                break;
            case SPECIAL:
                float spX = shoulderX + dir * 30f;
                g2d.draw(new Line2D.Float(shoulderX, shoulderY - 3f, spX, shoulderY - 8f));
                g2d.draw(new Line2D.Float(shoulderX, shoulderY + 3f, spX, shoulderY + 8f));
                Color lineColor = toColor(preset.getLineColor());
                Color glow = new Color(lineColor.getRed(),
                        lineColor.getGreen(),
                        lineColor.getBlue(), 100);
                g2d.setColor(glow);
                g2d.setStroke(new BasicStroke(preset.getLineWidth() + 2f));
                g2d.drawOval((int)(x - 30), (int)(shoulderY - 25), 60, 50);
                g2d.setStroke(new BasicStroke(preset.getLineWidth(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2d.setColor(lineColor);
                break;
            case BLOCK:
                g2d.draw(new Line2D.Float(shoulderX, shoulderY, shoulderX - dir * 10f, shoulderY + LIMB_LENGTH * 0.6f));
                g2d.draw(new Line2D.Float(shoulderX, shoulderY, shoulderX + dir * 10f, shoulderY + LIMB_LENGTH * 0.6f));
                break;
            default:
                drawArm(g2d, shoulderX, shoulderY, dir, true);
                drawArm(g2d, shoulderX, shoulderY, -dir, false);
                break;
        }

        // 腿
        float hipOffsetX = bodyLean * 0.6f;
        switch (stance) {
            case KICK:
                drawLeg(g2d, x + hipOffsetX, hipY, -dir * 0.2f, 1.2f);
                float kickX = x + hipOffsetX + dir * 25f;
                g2d.draw(new Line2D.Float(x + hipOffsetX, hipY, kickX, hipY - LIMB_LENGTH * 0.4f));
                g2d.draw(new Line2D.Float(kickX, hipY - LIMB_LENGTH * 0.4f, kickX + dir * 10f, y - LIMB_LENGTH * 0.6f));
                g2d.fillOval((int)(kickX + dir * 5 - 3), (int)(y - LIMB_LENGTH * 0.6f - 3), 10, 6);
                break;
            case DASH:
                drawLeg(g2d, x + hipOffsetX, hipY, dir * 0.6f, 1.3f);
                drawLeg(g2d, x + hipOffsetX, hipY, -dir * 0.4f, 1.1f);
                g2d.setStroke(new BasicStroke(1f));
                for (int i = 0; i < 3; i++) {
                    float lx = x - dir * (20 + i * 10);
                    g2d.draw(new Line2D.Float(lx, neckY + i * 8, lx - dir * 12, neckY + i * 8));
                }
                g2d.setStroke(new BasicStroke(preset.getLineWidth(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                break;
            default:
                drawLeg(g2d, x + hipOffsetX, hipY, dir * 0.2f, 1.0f);
                drawLeg(g2d, x + hipOffsetX, hipY, -dir * 0.2f, 1.0f);
                break;
        }
    }

    private static void drawArm(Graphics2D g, float sx, float sy, float dir, boolean isFront) {
        float yOff = isFront ? -2f : 2f;
        g.draw(new Line2D.Float(sx, sy + yOff,
                sx + dir * 8f, sy + LIMB_LENGTH * 0.6f + yOff));
        g.draw(new Line2D.Float(sx + dir * 8f, sy + LIMB_LENGTH * 0.6f + yOff,
                sx + dir * 6f, sy + LIMB_LENGTH + yOff));
    }

    private static void drawLeg(Graphics2D g, float hx, float hy, float dirX, float scale) {
        float kneeX = hx + dirX * 8f;
        float kneeY = hy + LIMB_LENGTH * 0.7f * scale;
        float footX = kneeX + dirX * 6f;
        float footY = kneeY + LIMB_LENGTH * 0.8f;
        g.draw(new Line2D.Float(hx, hy, kneeX, kneeY));
        g.draw(new Line2D.Float(kneeX, kneeY, footX, footY));
    }

    // ========== UI ==========

    public static void drawHealthBar(Graphics2D g, int x, int y, int width, int height,
                                      int health, int maxHealth, Color color, boolean facingRight) {
        float ratio = Math.max(0, (float) health / maxHealth);
        g.setColor(Color.DARK_GRAY);
        g.fillRect(x, y, width, height);
        g.setColor(color);
        if (facingRight) {
            g.fillRect(x, y, (int)(width * ratio), height);
        } else {
            int fillW = (int)(width * ratio);
            g.fillRect(x + width - fillW, y, fillW, height);
        }
        g.setColor(Color.WHITE);
        g.drawRect(x, y, width, height);
    }

    public static void drawTimer(Graphics2D g, int remainingSeconds, int panelWidth) {
        g.setFont(new Font("Monospaced", Font.BOLD, 36));
        g.setColor(remainingSeconds <= 10 ? Color.RED : Color.WHITE);
        String text = String.format("%d:%02d", remainingSeconds / 60, remainingSeconds % 60);
        FontMetrics fm = g.getFontMetrics();
        int tx = (panelWidth - fm.stringWidth(text)) / 2;
        g.drawString(text, tx, 40);
    }

    public static void drawAttackBox(Graphics2D g, Rectangle attackBox) {
        if (attackBox.width == 0 && attackBox.height == 0) return;
        g.setColor(new Color(255, 0, 0, 80));
        g.fill(attackBox);
        g.setColor(Color.RED);
        g.draw(attackBox);
    }
}
