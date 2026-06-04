package com.Fyren.render.libgdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.Fyren.game.FighterPreset;

/**
 * libGDX 游戏入口 — 管理 Screen 切换。
 *
 * Desktop: LWJGL3 后端，完整 UDP 网络对战
 * WebGL:   GWT 后端编译为 JS，本地双人对战 Demo
 */
public class FyrenGame extends ApplicationAdapter {

    private static FighterPreset demoP1Preset = FighterPreset.TAKESHI;
    private static FighterPreset demoP2Preset = FighterPreset.GOU;
    private static String netServerIp;
    private static int netServerPort;
    private static FighterPreset netPreset;
    private static String mode = "demo";

    private GameScreen gameScreen;

    /** 创建 Demo 双人本地模式实例 */
    public static FyrenGame createDemo(FighterPreset p1Preset, FighterPreset p2Preset) {
        mode = "demo";
        demoP1Preset = p1Preset;
        demoP2Preset = p2Preset;
        return new FyrenGame();
    }

    /** 创建网络对战模式实例 */
    public static FyrenGame createNetworkClient(String serverIp, int port, FighterPreset preset) {
        mode = "network";
        netServerIp = serverIp;
        netServerPort = port;
        netPreset = preset;
        return new FyrenGame();
    }

    @Override
    public void create() {
        if ("demo".equals(mode)) {
            gameScreen = GameScreen.createDemo(demoP1Preset, demoP2Preset);
        } else {
            gameScreen = GameScreen.createNetwork();
        }

        // 注入渲染组件
        gameScreen.setSpriteRenderer(new SpriteRenderer());
        gameScreen.setHudRenderer(new HudRenderer());
        gameScreen.setHitEffects(new HitEffects());
        gameScreen.setParticleEffects(new ParticleEffects());
        gameScreen.setMotionTrailEffect(new MotionTrailEffect());

        Gdx.graphics.setTitle("Fyren — " +
            ("demo".equals(mode)
                ? demoP1Preset.getDisplayName() + " vs " + demoP2Preset.getDisplayName()
                : "Online Match"));
    }

    @Override
    public void render() {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (gameScreen != null) {
            gameScreen.update(Gdx.graphics.getDeltaTime());
            gameScreen.render();
        }
    }

    @Override
    public void dispose() {
        if (gameScreen != null) {
            gameScreen.dispose();
        }
    }
}
