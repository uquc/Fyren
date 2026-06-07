package com.Fyren.render.libgdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.Fyren.game.Fighter;
import com.Fyren.game.FighterPreset;
import com.Fyren.game.GameWorld;

/**
 * HUD 渲染 — 血条、计时器、特殊技指示灯、胜负画面。
 * 使用 libGDX 内置 BitmapFont，不依赖外部字体文件。
 */
public class HudRenderer {

    private final BitmapFont font;
    private final ShapeRenderer shapes;
    private final SpriteBatch batch;

    // 血条平滑衰减
    private float displayHp1 = -1;
    private float displayHp2 = -1;

    private static final float HEALTH_BAR_W = 280f;
    private static final float HEALTH_BAR_H = 18f;

    public HudRenderer() {
        font = new BitmapFont();
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font.setColor(Color.WHITE);
    }

    /** 渲染完整 HUD */
    public void render(GameWorld world, OrthographicCamera camera) {
        Fighter p1 = world.getPlayer1();
        Fighter p2 = world.getPlayer2();

        // 初始化平滑血量
        if (displayHp1 < 0) {
            displayHp1 = p1.getHealth();
            displayHp2 = p2.getHealth();
        }

        // 平滑插值
        displayHp1 += (p1.getHealth() - displayHp1) * 0.12f;
        displayHp2 += (p2.getHealth() - displayHp2) * 0.12f;

        // 血条（屏幕固定坐标，使用 OrthographicCamera 的 viewport）
        float viewW = camera.viewportWidth;
        float viewH = camera.viewportHeight;

        drawHealthBar(40, viewH - 60, p1, displayHp1);
        drawHealthBar(viewW - 40 - HEALTH_BAR_W, viewH - 60, p2, displayHp2);

        // 计时器（手动格式化，GWT 不支持 String.format）
        int timeLeft = Math.max(0, world.getTimerFrames() / 60);
        int minutes = timeLeft / 60;
        int seconds = timeLeft % 60;
        String timeStr = pad2(minutes) + ":" + pad2(seconds);
        drawCenteredText(timeStr, viewW / 2f, viewH - 30, 1.2f);

        // 特殊技指示灯
        drawSpecialIndicator(40, viewH - 90, p1);
        drawSpecialIndicator(viewW - 40 - 20, viewH - 90, p2);

        // 胜负画面
        if (world.isGameOver()) {
            drawGameOver(world.getWinnerId(), viewW / 2f, viewH / 2f + 30);
        }
    }

    // ========== 内部绘制方法 ==========

    private void drawHealthBar(float x, float y, Fighter f, float displayHp) {
        int maxHp = f.getMaxHealth();
        float ratio = Math.max(0, displayHp / maxHp);
        float actualRatio = Math.max(0, (float) f.getHealth() / maxHp);

        // 背景
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.15f, 0.15f, 0.15f, 0.85f);
        shapes.rect(x, y, HEALTH_BAR_W, HEALTH_BAR_H);

        // 血量（平滑显示）
        Color barColor = ratio > 0.5f ? Color.GREEN : (ratio > 0.25f ? Color.ORANGE : Color.RED);
        shapes.setColor(barColor);
        shapes.rect(x, y, HEALTH_BAR_W * ratio, HEALTH_BAR_H);

        // 实际血量差值线（白色闪烁提示）
        if (Math.abs(displayHp - f.getHealth()) > 2f) {
            shapes.setColor(1f, 1f, 1f, 0.6f);
            shapes.rect(x + HEALTH_BAR_W * actualRatio, y, 2, HEALTH_BAR_H);
        }

        // 边框
        shapes.setColor(Color.WHITE);
        shapes.rect(x, y, HEALTH_BAR_W, HEALTH_BAR_H);
        shapes.end();

        // 角色名
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, f.getPreset().getDisplayName(), x, y + HEALTH_BAR_H + 14);
        batch.end();
    }

    private void drawCenteredText(String text, float x, float y, float scale) {
        batch.begin();
        font.getData().setScale(scale);
        font.setColor(Color.YELLOW);
        // BitmapFont 没有直接测量文本宽度，用近似
        float textW = text.length() * 8 * scale;
        font.draw(batch, text, x - textW / 2f, y);
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    private void drawSpecialIndicator(float x, float y, Fighter f) {
        boolean ready = f.isSpecialReady();
        Color indicatorColor;
        if (ready) {
            switch (f.getPreset()) {
                case KAGE:    indicatorColor = new Color(0.4f, 0.4f, 1f, 1f); break;
                case TAKESHI: indicatorColor = new Color(1f, 0.4f, 0.2f, 1f); break;
                case GOU:     indicatorColor = new Color(0.2f, 1f, 0.2f, 1f); break;
                default:      indicatorColor = Color.GRAY; break;
            }
        } else {
            indicatorColor = new Color(0.25f, 0.25f, 0.25f, 0.5f);
        }

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(indicatorColor);
        shapes.circle(x + 10, y, 7);
        shapes.setColor(Color.WHITE);
        shapes.circle(x + 10, y, 7);
        shapes.end();
    }

    private void drawGameOver(int winnerId, float x, float y) {
        String text;
        Color color;
        if (winnerId == 0) {
            text = "DRAW";
            color = Color.YELLOW;
        } else {
            text = "P" + winnerId + " WINS!";
            color = Color.GOLD;
        }

        batch.begin();
        font.getData().setScale(1.8f);
        font.setColor(color);
        float textW = text.length() * 9 * 1.8f;
        font.draw(batch, text, x - textW / 2f, y);
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    public void dispose() {
        font.dispose();
        shapes.dispose();
        batch.dispose();
    }

    /** GWT-compatible zero-padding helper (GWT 不支持 String.format) */
    private static String pad2(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }
}
