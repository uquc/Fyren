package com.Fyren.render.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.Fyren.game.FighterPreset;

/**
 * 角色选择画面 — 三张卡片横排，← → 切换高亮。
 * Demo 模式：P1 选完 → 显示 "PLAYER 2 SELECT" → P2 选完 → FIGHT。
 * 网络模式：选完 → MATCHING。
 */
public class CharacterSelectScreen extends AbstractScreen {

    private static final FighterPreset[] PRESETS = FighterPreset.values();
    private static final String[] ARCHETYPES = {
        "Assassin  ·  CD Recovery",
        "Striker   ·  Dmg Charge",
        "Vanguard  ·  Tank Charge"
    };

    private static final float CARD_W = 190f;
    private static final float CARD_H = 280f;
    private static final float CARD_GAP = 16f;
    private static final float CARD_Y = 100f;
    private static final float SIDE_SCALE = 0.78f;
    private static final float CENTER_SCALE = 1.08f;

    private int selectionIndex = 1; // default: middle (TAKESHI)
    private boolean isP2Phase = false; // set by FyrenGame for demo P2

    // Slide animation
    private float slideOffset = 0f;
    private float slideTarget = 0f;
    private static final float SLIDE_SPEED = 12f;

    // Edge detection
    private boolean leftWasDown = false;
    private boolean rightWasDown = false;
    private boolean enterWasDown = false;
    private boolean escWasDown = false;

    public CharacterSelectScreen(FyrenGame game, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        super(game, shapes, batch, font);
    }

    @Override
    public void enter() {
        selectionIndex = 1;
        slideOffset = 0f;
        slideTarget = 0f;
        isP2Phase = false;
    }

    /** FyrenGame 调用 — 进入 Demo P2 选择阶段 */
    public void startP2Phase() {
        isP2Phase = true;
        selectionIndex = 1;
        slideOffset = 0f;
        slideTarget = 0f;
    }

    @Override
    public void render(float delta) {
        handleInput();

        // Animate slide
        if (Math.abs(slideOffset - slideTarget) > 0.5f) {
            slideOffset += (slideTarget - slideOffset) * Math.min(SLIDE_SPEED * delta, 1f);
        } else {
            slideOffset = slideTarget;
        }

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Header
        batch.begin();
        font.setColor(0.47f, 0.47f, 0.47f, 1f);
        font.getData().setScale(1.0f);
        String header = isP2Phase ? "PLAYER 2 SELECT YOUR FIGHTER" : "SELECT YOUR FIGHTER";
        font.draw(batch, header, 40, 500);
        batch.end();

        // Cards
        float viewW = 960f;
        float centerX = viewW / 2f;

        for (int i = 0; i < PRESETS.length; i++) {
            int offsetFromCenter = i - 1;
            float targetX = centerX + offsetFromCenter * (CARD_W + CARD_GAP) + slideOffset;
            boolean isSelected = (i == selectionIndex);
            float scale = isSelected ? CENTER_SCALE : SIDE_SCALE;
            float alpha = isSelected ? 1f : 0.45f;

            drawCard(targetX, CARD_Y, CARD_W, CARD_H, scale, alpha, isSelected, PRESETS[i], i);
        }

        // Navigation dots
        float dotY = 70f;
        for (int i = 0; i < PRESETS.length; i++) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(i == selectionIndex ? 0.898f : 0.2f, i == selectionIndex ? 0.22f : 0.2f, i == selectionIndex ? 0.275f : 0.2f, 1f);
            shapes.circle(centerX + (i - 1) * 24f, dotY, 5f);
            shapes.end();
        }

        // Key hints
        batch.begin();
        font.setColor(0.33f, 0.33f, 0.33f, 1f);
        font.getData().setScale(0.9f);
        font.draw(batch, "← → Navigate   ENTER Confirm   ESC Back", 40, 30);
        batch.end();
    }

    private void drawCard(float cx, float cy, float w, float h, float scale, float alpha,
                          boolean selected, FighterPreset preset, int index) {
        float sw = w * scale;
        float sh = h * scale;
        float sx = cx - sw / 2f;
        float sy = cy;

        // Background
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(selected ? 0.102f : 0.067f, selected ? 0.102f : 0.067f, selected ? 0.18f : 0.067f, alpha);
        shapes.rect(sx, sy, sw, sh);
        shapes.end();

        // Border
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(selected ? 0.898f : 0.133f, selected ? 0.22f : 0.133f, selected ? 0.275f : 0.133f, alpha);
        shapes.rect(sx, sy, sw, sh);
        shapes.end();

        // Silhouette placeholder
        float silW = sw - 20f;
        float silH = sh * 0.45f;
        float silX = sx + 10f;
        float silY = sy + sh - silH - 10f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.086f, 0.106f, 0.133f, alpha);
        shapes.rect(silX, silY, silW, silH);
        shapes.end();

        // Silhouette border (colored by character theme)
        int lc = preset.getLineColor();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(((lc >> 16) & 0xFF) / 255f, ((lc >> 8) & 0xFF) / 255f, (lc & 0xFF) / 255f, alpha);
        shapes.rect(silX, silY, silW, silH);
        shapes.end();

        // Name
        batch.begin();
        float nameY = silY - 8f;
        font.setColor(selected ? 0.945f : 0.47f, selected ? 0.98f : 0.47f, selected ? 0.937f : 0.47f, alpha);
        font.getData().setScale(selected ? 1.4f * scale : 1.1f * scale);
        String name = preset.getDisplayName();
        float nameW = name.length() * 9 * font.getData().scaleX;
        font.draw(batch, name, sx + sw / 2f - nameW / 2f, nameY);
        font.getData().setScale(1.0f);
        batch.end();

        // Archetype
        batch.begin();
        font.setColor(0.33f, 0.33f, 0.33f, alpha);
        font.getData().setScale(0.7f * scale);
        String arch = ARCHETYPES[index];
        float archW = arch.length() * 5 * font.getData().scaleX;
        font.draw(batch, arch, sx + sw / 2f - archW / 2f, nameY - 16f);
        font.getData().setScale(1.0f);
        batch.end();

        // Stats bars (selected only)
        if (selected) {
            float statX = sx + 14f;
            float statY = sy + 6f;
            float barW = sw - 28f;
            float barH = 8f;
            float barGap = 16f;

            drawStatBar(statX, statY + barGap * 2, barW, barH, (float)preset.getMaxHealth() / 130f,
                new Color(0.31f, 0.8f, 0.77f, 1f), "HP " + preset.getMaxHealth());
            drawStatBar(statX, statY + barGap, barW, barH, preset.getForwardSpeed() / 7f,
                new Color(1f, 0.9f, 0.43f, 1f), "SPD " + preset.getForwardSpeed());
            drawStatBar(statX, statY, barW, barH, (float)preset.getBaseDamage() / 15f,
                new Color(1f, 0.42f, 0.42f, 1f), "DMG " + preset.getBaseDamage());
        }
    }

    private void drawStatBar(float x, float y, float w, float h, float ratio, Color color, String label) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.1f, 0.1f, 0.1f, 0.6f);
        shapes.rect(x, y, w, h);
        shapes.setColor(color.r, color.g, color.b, 0.8f);
        shapes.rect(x, y, w * Math.min(ratio, 1f), h);
        shapes.end();

        batch.begin();
        font.setColor(0.6f, 0.6f, 0.6f, 1f);
        font.getData().setScale(0.6f);
        font.draw(batch, label, x + 2, y + h - 1);
        font.getData().setScale(1.0f);
        batch.end();
    }

    private void handleInput() {
        boolean left = Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A);
        boolean right = Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D);
        boolean enter = Gdx.input.isKeyPressed(Input.Keys.ENTER);
        boolean esc = Gdx.input.isKeyPressed(Input.Keys.ESCAPE);

        if (left && !leftWasDown && selectionIndex > 0) {
            selectionIndex--;
            slideTarget += (CARD_W + CARD_GAP);
        }
        if (right && !rightWasDown && selectionIndex < PRESETS.length - 1) {
            selectionIndex++;
            slideTarget -= (CARD_W + CARD_GAP);
        }
        if (enter && !enterWasDown) {
            game.onCharacterSelected(PRESETS[selectionIndex]);
        }
        if (esc && !escWasDown) {
            game.goToTitle();
        }

        leftWasDown = left;
        rightWasDown = right;
        enterWasDown = enter;
        escWasDown = esc;
    }
}
