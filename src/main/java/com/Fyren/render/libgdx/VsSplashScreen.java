package com.Fyren.render.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.Fyren.GameClient;
import com.Fyren.game.FighterPreset;

/**
 * VS 对阵展示 — P1 vs P2 并排，2.5s 自动进入对战或按任意键跳过。
 */
public class VsSplashScreen extends AbstractScreen {

    private static final float DURATION = 2.5f;
    private float timer = 0f;
    private boolean anyKeyWasDown = false;

    private String p1Name;
    private FighterPreset p1Preset;
    private int p1Mmr;
    private String p2Name;
    private FighterPreset p2Preset;
    private int p2Mmr;

    public VsSplashScreen(FyrenGame game, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        super(game, shapes, batch, font);
    }

    @Override
    public void enter() {
        timer = 0f;
        anyKeyWasDown = true; // prevent immediate skip from held key from previous screen

        GameClient client = game.getGameClient();
        if (client != null) {
            p1Name = "YOU";
            p1Preset = client.getPreset();
            p1Mmr = client.getPlayerRating().getRating();

            p2Name = "OPPONENT";
            p2Preset = FighterPreset.values()[client.getOpponentPresetOrdinal()];
            p2Mmr = client.getOpponentRating();

            client.startGame(); // FrameSyncManager + P2P handshake
        }
    }

    @Override
    public void render(float delta) {
        timer += delta;

        boolean anyKey = Gdx.input.isKeyPressed(Input.Keys.ANY_KEY);
        if (anyKey && !anyKeyWasDown && timer > 0.3f) { game.startFight(); return; }
        anyKeyWasDown = anyKey;

        if (timer >= DURATION) { game.startFight(); return; }

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float cx = 480f;
        float cy = 270f;

        drawFighterCard(200f, cy, p1Name, p1Preset, p1Mmr, false);
        drawFighterCard(760f, cy, p2Name, p2Preset, p2Mmr, true);

        // VS center
        batch.begin();
        font.setColor(0.898f, 0.22f, 0.275f, 1f);
        font.getData().setScale(3.5f);
        font.draw(batch, "VS", cx - 30f, cy + 20f);

        font.setColor(0.33f, 0.33f, 0.33f, 1f);
        font.getData().setScale(0.9f);
        int remaining = (int) Math.ceil(DURATION - timer);
        font.draw(batch, "READY... " + remaining, cx - 30f, cy - 50f);
        font.getData().setScale(1.0f);
        batch.end();
    }

    private void drawFighterCard(float cx, float cy, String label, FighterPreset preset, int mmr, boolean isOpponent) {
        float cardW = 160f;
        float cardH = 200f;
        float x = cx - cardW / 2f;
        float y = cy - cardH / 2f;

        // Silhouette
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.086f, 0.106f, 0.133f, 1f);
        shapes.rect(x, y + 40f, cardW, 120f);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(isOpponent ? 0.43f : 0.33f, isOpponent ? 0.56f : 0.33f, isOpponent ? 0.98f : 0.33f, 0.6f);
        shapes.rect(x, y + 40f, cardW, 120f);
        shapes.end();

        // Label
        batch.begin();
        font.setColor(isOpponent ? new Color(0.43f, 0.56f, 0.98f, 1f) : Color.WHITE);
        font.getData().setScale(1.2f);
        font.draw(batch, label, cx - label.length() * 6f, y + 30f);
        font.getData().setScale(1.0f);
        batch.end();

        // Character name
        batch.begin();
        font.setColor(0.945f, 0.98f, 0.937f, 1f);
        font.getData().setScale(1.0f);
        String name = preset.getDisplayName();
        font.draw(batch, name, cx - name.length() * 5f, y + 12f);
        batch.end();

        // MMR
        batch.begin();
        font.setColor(0.47f, 0.47f, 0.47f, 1f);
        font.getData().setScale(0.8f);
        font.draw(batch, "MMR " + mmr, cx - 20f, y - 4f);
        font.getData().setScale(1.0f);
        batch.end();
    }
}
