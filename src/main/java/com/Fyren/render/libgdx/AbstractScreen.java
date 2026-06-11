package com.Fyren.render.libgdx;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * 所有菜单 Screen 的基类。
 * 持有共享渲染组件引用 + FyrenGame 回引（用于触发画面切换）。
 * 各 Screen 自行通过 Gdx.input.isKeyPressed() 处理输入。
 */
public abstract class AbstractScreen {

    protected final FyrenGame game;
    protected final ShapeRenderer shapes;
    protected final SpriteBatch batch;
    protected final BitmapFont font;

    protected AbstractScreen(FyrenGame game, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        this.game = game;
        this.shapes = shapes;
        this.batch = batch;
        this.font = font;
    }

    /** 画面激活时调用一次 */
    public abstract void enter();

    /** 每帧渲染 */
    public abstract void render(float delta);

    /** 释放资源（默认空实现，子类按需覆盖） */
    public void dispose() {}
}
