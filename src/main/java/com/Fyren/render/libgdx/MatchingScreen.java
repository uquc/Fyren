package com.Fyren.render.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.Fyren.GameClient;

/**
 * 匹配等待画面 — "SEARCHING FOR OPPONENT..." + 旋转 VS 弧线。
 * 轮询 GameClient 状态检测匹配成功/失败。
 */
public class MatchingScreen extends AbstractScreen {

    private float elapsed = 0f;
    private float rotationAngle = 0f;
    private String statusText = "";
    private float statusTimer = 0f;
    private boolean escWasDown = false;

    // Animated dots
    private int dotCount = 0;
    private float dotTimer = 0f;

    public MatchingScreen(FyrenGame game, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        super(game, shapes, batch, font);
    }

    @Override
    public void enter() {
        elapsed = 0f;
        rotationAngle = 0f;
        statusText = "";
        statusTimer = 0f;
        dotCount = 0;
        dotTimer = 0f;

        GameClient client = game.getGameClient();
        if (client != null) {
            try {
                client.connect();
            } catch (Exception e) {
                statusText = "CONNECTION FAILED";
                statusTimer = 3f;
                return;
            }
            client.requestMatch();
        }
    }

    @Override
    public void render(float delta) {
        elapsed += delta;
        rotationAngle += delta * 180f;

        dotTimer += delta;
        if (dotTimer > 0.35f) { dotTimer = 0f; dotCount = (dotCount + 1) % 4; }

        // Poll GameClient state
        GameClient client = game.getGameClient();
        if (client != null && statusTimer <= 0f) {
            GameClient.ClientState st = client.getState();
            if (st == GameClient.ClientState.MATCHED) {
                game.onMatchFound();
                return;
            }
        }

        if (statusTimer > 0f) {
            statusTimer -= delta;
            if (statusTimer <= 0f) statusText = "";
        }

        // ESC → cancel
        boolean esc = Gdx.input.isKeyPressed(Input.Keys.ESCAPE);
        if (esc && !escWasDown) {
            if (client != null) client.cancelMatch();
            game.goToCharacterSelect();
            return;
        }
        escWasDown = esc;

        // --- draw ---
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float viewW = 960f;

        // Header
        batch.begin();
        font.setColor(0.33f, 0.33f, 0.33f, 1f);
        font.getData().setScale(0.9f);
        font.draw(batch, "MATCHMAKING", 80, 420);
        font.getData().setScale(1.0f);
        batch.end();

        // Main text with dots
        batch.begin();
        font.setColor(0.945f, 0.98f, 0.937f, 1f);
        font.getData().setScale(1.6f);
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < dotCount; i++) dots.append(".");
        font.draw(batch, "SEARCHING FOR", 80, 360);
        font.draw(batch, "OPPONENT" + dots.toString(), 80, 330);
        font.getData().setScale(1.0f);
        batch.end();

        // Info
        batch.begin();
        font.setColor(0.47f, 0.47f, 0.47f, 1f);
        font.getData().setScale(0.9f);
        if (client != null) {
            int rating = client.getPlayerRating().getRating();
            font.draw(batch, "ELO Range: " + (rating - 100) + " – " + (rating + 100), 80, 280);
        }
        int secs = (int) elapsed;
        font.draw(batch, "Time elapsed: " + secs + "s", 80, 258);
        font.getData().setScale(1.0f);
        batch.end();

        // Status text
        if (!statusText.isEmpty()) {
            batch.begin();
            font.setColor(1f, 0.3f, 0.3f, 1f);
            font.getData().setScale(1.2f);
            font.draw(batch, statusText, 80, 200);
            font.getData().setScale(1.0f);
            batch.end();
        }

        // ESC hint
        batch.begin();
        font.setColor(0.33f, 0.33f, 0.33f, 1f);
        font.getData().setScale(0.9f);
        font.draw(batch, "ESC - Cancel Matchmaking", 80, 40);
        font.getData().setScale(1.0f);
        batch.end();

        // Rotating arc + VS
        float vsX = 720f;
        float vsY = 300f;
        float vsR = 40f;

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.2f, 0.2f, 0.2f, 1f);
        shapes.circle(vsX, vsY, vsR + 8f);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.898f, 0.22f, 0.275f, 0.8f);
        int segments = 36;
        float arcLen = 200f;
        for (int i = 0; i < segments; i++) {
            float a1 = (rotationAngle + i * arcLen / segments) * (float) Math.PI / 180f;
            float a2 = (rotationAngle + (i + 1) * arcLen / segments) * (float) Math.PI / 180f;
            shapes.line(
                vsX + (float) Math.cos(a1) * vsR, vsY + (float) Math.sin(a1) * vsR,
                vsX + (float) Math.cos(a2) * vsR, vsY + (float) Math.sin(a2) * vsR
            );
        }
        shapes.end();

        batch.begin();
        font.setColor(0.898f, 0.22f, 0.275f, 1f);
        font.getData().setScale(1.5f);
        font.draw(batch, "VS", vsX - 14f, vsY + 7f);
        font.getData().setScale(1.0f);
        batch.end();
    }
}
