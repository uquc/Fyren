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
 * 结算画面 — 胜负 + 统计 + Rematch/返回菜单。
 * 30s 无操作自动返回标题。
 */
public class ResultScreen extends AbstractScreen {

    private static final float AUTO_RETURN = 30f;

    private String resultText;
    private Color resultColor;
    private int mmrChange;
    private int healthRemaining;
    private int maxHealth;
    private int fightDurationSecs;
    private float timer;
    private int selectionIndex = 0; // 0=REMATCH, 1=MENU

    private boolean upWasDown = false;
    private boolean downWasDown = false;
    private boolean enterWasDown = false;

    public ResultScreen(FyrenGame game, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        super(game, shapes, batch, font);
    }

    @Override
    public void enter() {
        timer = 0f;
        selectionIndex = 0;

        int winnerId = game.getResultWinnerId();
        GameClient client = game.getGameClient();

        if (winnerId < 0) {
            resultText = "DRAW";
            resultColor = Color.YELLOW;
            mmrChange = 0;
        } else if (client != null && winnerId == client.getLocalPlayerId()) {
            resultText = "YOU WIN";
            resultColor = Color.GOLD;
            mmrChange = 18;
        } else {
            resultText = "YOU LOSE";
            resultColor = new Color(0.6f, 0.6f, 0.6f, 1f);
            mmrChange = -15;
        }

        healthRemaining = game.getResultHealth();
        maxHealth = game.getResultMaxHealth();
        fightDurationSecs = game.getResultDurationSecs();
    }

    @Override
    public void render(float delta) {
        timer += delta;
        if (timer >= AUTO_RETURN) { game.goToTitle(); return; }

        handleInput();

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float cx = 480f;
        float cy = 540f;

        // Result text
        batch.begin();
        font.setColor(resultColor);
        font.getData().setScale(3.2f);
        float textW = resultText.length() * 16 * 3.2f;
        font.draw(batch, resultText, cx - textW / 2f, cy - 60f);

        font.setColor(0.47f, 0.47f, 0.47f, 1f);
        font.getData().setScale(1.0f);
        font.draw(batch, "GREAT FIGHT", cx - 45f, cy - 90f);
        font.getData().setScale(1.0f);
        batch.end();

        // Stats
        String mmrStr = (mmrChange >= 0 ? "+" : "") + mmrChange;
        Color mmrColor = mmrChange >= 0 ? new Color(0.18f, 0.72f, 0.27f, 1f) : new Color(1f, 0.3f, 0.3f, 1f);
        String hpStr = healthRemaining + " / " + maxHealth;
        String timeStr = fightDurationSecs + "s";

        float statY = cy - 130f;
        drawStat(cx - 160f, statY, "MMR", mmrStr, mmrColor);
        drawStat(cx, statY, "HEALTH LEFT", hpStr, Color.WHITE);
        drawStat(cx + 160f, statY, "TIME", timeStr, Color.WHITE);

        // Menu
        float menuY = cy - 195f;
        String[] items = {"REMATCH", "RETURN TO MENU"};
        batch.begin();
        for (int i = 0; i < items.length; i++) {
            boolean sel = (i == selectionIndex);
            font.setColor(sel ? 0.945f : 0.47f, sel ? 0.98f : 0.47f, sel ? 0.937f : 0.47f, 1f);
            font.getData().setScale(1.2f);
            String prefix = sel ? "▸ " : "  ";
            font.draw(batch, prefix + items[i], cx - 80f, menuY - i * 35f);
        }
        font.getData().setScale(1.0f);
        batch.end();

        // Countdown
        batch.begin();
        font.setColor(0.33f, 0.33f, 0.33f, 1f);
        font.getData().setScale(0.8f);
        int remaining = (int) Math.ceil(AUTO_RETURN - timer);
        font.draw(batch, "Auto-return to menu in " + remaining + "s...", cx - 90f, 30f);
        font.getData().setScale(1.0f);
        batch.end();
    }

    private void drawStat(float cx, float y, String label, String value, Color valueColor) {
        batch.begin();
        font.setColor(0.33f, 0.33f, 0.33f, 1f);
        font.getData().setScale(0.8f);
        font.draw(batch, label, cx - label.length() * 3f, y);
        font.setColor(valueColor);
        font.getData().setScale(1.1f);
        font.draw(batch, value, cx - value.length() * 5f, y - 20f);
        font.getData().setScale(1.0f);
        batch.end();
    }

    private void handleInput() {
        boolean up = Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W);
        boolean down = Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S);
        boolean enter = Gdx.input.isKeyPressed(Input.Keys.ENTER);

        if (up && !upWasDown) selectionIndex = Math.max(0, selectionIndex - 1);
        if (down && !downWasDown) selectionIndex = Math.min(1, selectionIndex + 1);
        if (enter && !enterWasDown) {
            if (selectionIndex == 0) game.requestRematch();
            else game.goToTitle();
        }

        upWasDown = up;
        downWasDown = down;
        enterWasDown = enter;
    }
}
