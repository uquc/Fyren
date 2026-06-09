package com.Fyren.render.libgdx.gwt;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;
import com.badlogic.gdx.backends.gwt.preloader.Preloader.PreloaderCallback;
import com.badlogic.gdx.backends.gwt.preloader.Preloader.PreloaderState;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.Fyren.game.Fighter;
import com.Fyren.game.FighterPreset;
import com.Fyren.game.GameWorld;
import com.Fyren.render.libgdx.CameraController;
import com.Fyren.render.libgdx.GdxInputHandler;
import com.Fyren.render.libgdx.HitEffects;
import com.Fyren.render.libgdx.HudRenderer;
import com.Fyren.render.libgdx.MotionTrailEffect;
import com.Fyren.render.libgdx.ParticleEffects;
import com.Fyren.render.libgdx.SpriteRenderer;
import com.Fyren.sync.InputCommand;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.VerticalPanel;

import java.util.ArrayList;
import java.util.List;

/**
 * GWT/WebGL 入口 — 本地 Demo 双人对战。
 *
 * 不依赖 FyrenGame.java 或 GameScreen.java（两者都引用 GameClient → java.net.*）。
 * 直接内联 Demo 游戏循环，仅使用 GWT 兼容的依赖。
 */
public class FyrenGwtLauncher extends GwtApplication implements ApplicationListener {

    // --- Game state ---
    private GameWorld gameWorld;
    private GdxInputHandler inputHandler;
    private CameraController cameraController;

    // --- Renderers ---
    private SpriteRenderer spriteRenderer;
    private HudRenderer hudRenderer;
    private HitEffects hitEffects;
    private ParticleEffects particleEffects;
    private MotionTrailEffect motionTrailEffect;

    // --- Background ---
    private ShapeRenderer bgShapes;

    private int frameNumber;

    @Override
    public GwtApplicationConfiguration getConfig() {
        return new GwtApplicationConfiguration(960, 540);
    }

    /** 自定义 preloader — 无 logo 依赖，空资源列表也能正常完成 */
    @Override
    public PreloaderCallback getPreloaderCallback() {
        final VerticalPanel preloaderPanel = new VerticalPanel();
        preloaderPanel.setStyleName("gdx-preloader");
        preloaderPanel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        preloaderPanel.setVerticalAlignment(HasVerticalAlignment.ALIGN_MIDDLE);

        final Label title = new Label("Fyren");
        title.setStyleName("gdx-title");
        preloaderPanel.add(title);

        final SimplePanel meterPanel = new SimplePanel();
        meterPanel.setStyleName("gdx-meter");
        final InlineHTML meter = new InlineHTML();
        final com.google.gwt.dom.client.Style meterStyle = meter.getElement().getStyle();
        meterStyle.setWidth(0, Unit.PCT);
        meterPanel.add(meter);
        preloaderPanel.add(meterPanel);

        getRootPanel().add(preloaderPanel);
        return new PreloaderCallback() {
            @Override
            public void error(String file) {
                System.out.println("preloader error: " + file);
            }

            @Override
            public void update(PreloaderState state) {
                meterStyle.setWidth(100f * state.getProgress(), Unit.PCT);
            }
        };
    }

    @Override
    public ApplicationListener createApplicationListener() {
        return this;
    }

    // === ApplicationListener ===

    @Override
    public void create() {
        Gdx.graphics.setTitle("Fyren WebGL — KAGE vs GOU");

        gameWorld = new GameWorld();
        gameWorld.setupPlayers(FighterPreset.KAGE, FighterPreset.GOU);

        inputHandler = new GdxInputHandler();

        cameraController = new CameraController(960, 540);

        spriteRenderer = new SpriteRenderer();
        hudRenderer = new HudRenderer();
        hitEffects = new HitEffects();
        particleEffects = new ParticleEffects();
        motionTrailEffect = new MotionTrailEffect();
        bgShapes = new ShapeRenderer();

        frameNumber = 0;
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        // === Update ===
        InputCommand cmd1 = inputHandler.samplePlayer1(frameNumber);
        InputCommand cmd2 = inputHandler.samplePlayer2(frameNumber);

        List<InputCommand> inputs = new ArrayList<>();
        inputs.add(cmd1);
        inputs.add(cmd2);

        Fighter p1 = gameWorld.getPlayer1();
        Fighter p2 = gameWorld.getPlayer2();
        int hp1Before = p1.getHealth();
        int hp2Before = p2.getHealth();

        // Hit stop 检测
        if (hitEffects.isInHitStop()) {
            hitEffects.update(delta);
        } else {
            gameWorld.update(inputs, frameNumber);
            frameNumber++;
        }

        int dmg1 = hp1Before - p1.getHealth();
        int dmg2 = hp2Before - p2.getHealth();

        if (dmg1 > 0) {
            hitEffects.onHit(p1, p2, p1.getLastRawDamageReceived());
            particleEffects.spawnHitSpark(p1.getX(), p1.getY() + 50);
            cameraController.shake(3f + dmg1 * 0.5f, 0.15f);
        }
        if (dmg2 > 0) {
            hitEffects.onHit(p2, p1, p2.getLastRawDamageReceived());
            particleEffects.spawnHitSpark(p2.getX(), p2.getY() + 50);
            cameraController.shake(3f + dmg2 * 0.5f, 0.15f);
        }

        hitEffects.update(delta);
        particleEffects.update(delta);
        motionTrailEffect.sample(p1, p2, delta);
        cameraController.update(p1, p2, delta);

        // === Render ===
        ScreenUtils.clear(0.08f, 0.08f, 0.12f, 1f);

        OrthographicCamera cam = cameraController.getCamera();

        drawBackground(cam);

        spriteRenderer.begin(cam);
        spriteRenderer.drawFighter(gameWorld.getPlayer1());
        spriteRenderer.drawFighter(gameWorld.getPlayer2());
        spriteRenderer.end();

        motionTrailEffect.render(cam);
        particleEffects.render(cam);
        hitEffects.render(cam);

        hudRenderer.render(gameWorld, cam);
    }

    @Override
    public void resize(int width, int height) {}

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void dispose() {
        bgShapes.dispose();
        if (spriteRenderer != null) spriteRenderer.dispose();
        if (hudRenderer != null) hudRenderer.dispose();
        if (particleEffects != null) particleEffects.dispose();
        if (motionTrailEffect != null) motionTrailEffect.dispose();
    }

    // === Background ===

    private void drawBackground(OrthographicCamera cam) {
        bgShapes.setProjectionMatrix(cam.combined);

        bgShapes.begin(ShapeRenderer.ShapeType.Filled);
        bgShapes.setColor(0.12f, 0.12f, 0.14f, 1f);
        bgShapes.rect(cam.position.x - 500, 0, 1000, 80);
        bgShapes.end();

        bgShapes.begin(ShapeRenderer.ShapeType.Line);
        bgShapes.setColor(0.25f, 0.25f, 0.28f, 0.6f);
        float startX = cam.position.x - 500;
        for (int i = 0; i < 25; i++) {
            float x1 = startX + i * 40;
            bgShapes.line(x1, 82, x1 + 18, 80);
        }
        bgShapes.end();

        bgShapes.begin(ShapeRenderer.ShapeType.Line);
        bgShapes.setColor(0.2f, 0.2f, 0.22f, 0.4f);
        bgShapes.line(480, 50, 480, 540);
        bgShapes.end();
    }
}
