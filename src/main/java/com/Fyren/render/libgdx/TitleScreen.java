package com.Fyren.render.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * 标题画面 — 左对齐 Logo + 3 项菜单。
 */
public class TitleScreen extends AbstractScreen {

    private static final String[] MENU_ITEMS = {
        "NETWORK MATCH",
        "TRAINING MODE",
        "EXIT"
    };

    private int selectionIndex = 0;

    // "Coming Soon" flash
    private boolean showComingSoon = false;
    private float comingSoonTimer = 0f;

    // Edge detection
    private boolean upWasDown = false;
    private boolean downWasDown = false;
    private boolean enterWasDown = false;

    public TitleScreen(FyrenGame game, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        super(game, shapes, batch, font);
    }

    @Override
    public void enter() {
        selectionIndex = 0;
        showComingSoon = false;
    }

    @Override
    public void render(float delta) {
        // --- input ---
        if (showComingSoon) {
            comingSoonTimer -= delta;
            if (comingSoonTimer <= 0f) showComingSoon = false;
        } else {
            handleInput();
        }

        // --- clear ---
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // --- Logo ---
        batch.begin();
        font.setColor(0.898f, 0.22f, 0.275f, 1f); // #e63946
        font.getData().setScale(3.5f);
        font.draw(batch, "風 蓮", 80, 420);

        font.getData().setScale(1.0f);
        font.setColor(0.33f, 0.33f, 0.33f, 1f);
        font.draw(batch, "F Y R E N", 84, 375);
        batch.end();

        // --- Menu items ---
        batch.begin();
        for (int i = 0; i < MENU_ITEMS.length; i++) {
            boolean sel = (i == selectionIndex);
            font.setColor(sel ? 0.945f : 0.47f, sel ? 0.98f : 0.47f, sel ? 0.937f : 0.47f, 1f);
            font.getData().setScale(1.3f);
            String prefix = sel ? "▸ " : "  ";
            font.draw(batch, prefix + MENU_ITEMS[i], 80, 260 - i * 45);
        }
        font.getData().setScale(1.0f);
        batch.end();

        // --- Version ---
        batch.begin();
        font.setColor(0.267f, 0.267f, 0.267f, 1f);
        font.draw(batch, "v0.2.0", 900, 20);
        batch.end();

        // --- Coming soon overlay ---
        if (showComingSoon) {
            batch.begin();
            font.setColor(1f, 1f, 0.3f, 1f);
            font.getData().setScale(1.5f);
            font.draw(batch, "COMING SOON", 80, 100);
            font.getData().setScale(1.0f);
            batch.end();
        }
    }

    private void handleInput() {
        boolean up = Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W);
        boolean down = Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S);
        boolean enter = Gdx.input.isKeyPressed(Input.Keys.ENTER);

        if (up && !upWasDown)
            selectionIndex = (selectionIndex - 1 + MENU_ITEMS.length) % MENU_ITEMS.length;
        if (down && !downWasDown)
            selectionIndex = (selectionIndex + 1) % MENU_ITEMS.length;
        if (enter && !enterWasDown) {
            switch (selectionIndex) {
                case 0: game.enterNetworkMatch(); break;
                case 1: showComingSoon = true; comingSoonTimer = 1.5f; break;
                case 2: Gdx.app.exit(); break;
            }
        }

        upWasDown = up;
        downWasDown = down;
        enterWasDown = enter;
    }
}
