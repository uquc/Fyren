package com.Fyren.render.libgdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.Fyren.GameClient;
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
    private static String mode = "demo";

    // 网络模式：预先创建好的 GameClient（由 FyrenLauncher 传入）
    private static GameClient sharedGameClient;

    private GameScreen gameScreen;
    private GameClient gameClient;

    /** 创建 Demo 双人本地模式实例 */
    public static FyrenGame createDemo(FighterPreset p1Preset, FighterPreset p2Preset) {
        mode = "demo";
        demoP1Preset = p1Preset;
        demoP2Preset = p2Preset;
        return new FyrenGame();
    }

    /** 创建网络对战模式实例（传入已匹配成功的 GameClient） */
    public static FyrenGame createNetworkClient(GameClient client) {
        mode = "network";
        sharedGameClient = client;
        return new FyrenGame();
    }

    @Override
    public void create() {
        if ("demo".equals(mode)) {
            gameScreen = GameScreen.createDemo(demoP1Preset, demoP2Preset);
        } else {
            gameClient = sharedGameClient;
            gameScreen = GameScreen.createNetwork();
            gameScreen.setGameClient(gameClient);

            // 启动帧同步游戏循环（FrameSyncManager 独立线程）
            gameClient.startGame();

            Gdx.graphics.setTitle("Fyren — Online Match (P" + gameClient.getLocalPlayerId() + " "
                + gameClient.getPreset().getDisplayName() + ")");
        }

        // 注入渲染组件
        gameScreen.setSpriteRenderer(new SpriteRenderer());
        gameScreen.setHudRenderer(new HudRenderer());
        gameScreen.setHitEffects(new HitEffects());
        gameScreen.setParticleEffects(new ParticleEffects());
        gameScreen.setMotionTrailEffect(new MotionTrailEffect());
        gameScreen.setAudioManager(new AudioManager());

        if ("demo".equals(mode)) {
            Gdx.graphics.setTitle("Fyren — "
                + demoP1Preset.getDisplayName() + " vs " + demoP2Preset.getDisplayName());
        }
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
        if (gameClient != null) {
            gameClient.disconnect();
        }
    }
}
