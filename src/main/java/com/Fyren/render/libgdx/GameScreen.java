package com.Fyren.render.libgdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.Fyren.game.Fighter;
import com.Fyren.game.FighterPreset;
import com.Fyren.game.GameWorld;
import com.Fyren.sync.InputCommand;

import java.util.ArrayList;
import java.util.List;

/**
 * 主游戏 Screen — 每帧采样输入、更新 GameWorld、渲染。
 *
 * 两种模式：
 * - Demo: 直接驱动 GameWorld（本地双人），无网络
 * - Network: 通过 GameClient + FrameSyncManager 驱动
 */
public class GameScreen {

    private final GameWorld gameWorld;
    private final GdxInputHandler inputHandler;
    private final CameraController cameraController;

    // 渲染组件
    private SpriteRenderer spriteRenderer;
    private HudRenderer hudRenderer;
    private HitEffects hitEffects;
    private ParticleEffects particleEffects;
    private MotionTrailEffect motionTrailEffect;
    private AudioManager audioManager;

    private int frameNumber = 0;
    private final boolean isNetworkMode;

    // 背景渲染
    private final ShapeRenderer bgShapes;

    // KO 音效去重
    private boolean koPlayed = false;

    // 网络模式引用
    private com.Fyren.GameClient gameClient = null;

    // 网络模式：上一帧血量（用于命中检测）
    private int netHp1Before = -1;
    private int netHp2Before = -1;

    // === 工厂方法 ===

    /** Demo 双人模式 */
    public static GameScreen createDemo(FighterPreset p1Preset, FighterPreset p2Preset) {
        GameWorld gw = new GameWorld();
        gw.setupPlayers(p1Preset, p2Preset);
        return new GameScreen(gw, false);
    }

    /** 网络对战模式 */
    public static GameScreen createNetwork() {
        GameWorld gw = new GameWorld();
        return new GameScreen(gw, true);
    }

    private GameScreen(GameWorld gameWorld, boolean isNetworkMode) {
        this.gameWorld = gameWorld;
        this.isNetworkMode = isNetworkMode;
        this.inputHandler = new GdxInputHandler();
        this.cameraController = new CameraController(960, 540);
        this.bgShapes = new ShapeRenderer();
    }

    // === 公开 setter ===

    public void setSpriteRenderer(SpriteRenderer r) { this.spriteRenderer = r; }
    public void setHudRenderer(HudRenderer r) { this.hudRenderer = r; }
    public void setHitEffects(HitEffects e) { this.hitEffects = e; }
    public void setParticleEffects(ParticleEffects e) { this.particleEffects = e; }
    public void setMotionTrailEffect(MotionTrailEffect e) { this.motionTrailEffect = e; }
    public void setAudioManager(AudioManager a) { this.audioManager = a; }
    public void setGameClient(com.Fyren.GameClient client) { this.gameClient = client; }

    public GameWorld getGameWorld() { return gameWorld; }

    // === 每帧更新 ===

    /** 由 FyrenGame.render() 每帧调用 */
    public void update(float delta) {
        if (isNetworkMode) {
            updateNetwork(delta);
        } else {
            updateDemo(delta);
        }
    }

    private void updateDemo(float delta) {
        // 采样双人输入
        InputCommand cmd1 = inputHandler.samplePlayer1(frameNumber);
        InputCommand cmd2 = inputHandler.samplePlayer2(frameNumber);

        List<InputCommand> inputs = new ArrayList<>();
        inputs.add(cmd1);
        inputs.add(cmd2);

        // 记录更新前血量（用于命中检测）
        Fighter p1 = gameWorld.getPlayer1();
        Fighter p2 = gameWorld.getPlayer2();
        int hp1Before = p1.getHealth();
        int hp2Before = p2.getHealth();

        // Hit stop — skip game world update during hit-stop frames
        if (hitEffects == null || !hitEffects.isInHitStop()) {
            gameWorld.update(inputs, frameNumber);
            frameNumber++;
        }

        // 命中检测与反馈
        int dmg1 = hp1Before - p1.getHealth();
        int dmg2 = hp2Before - p2.getHealth();

        if (dmg1 > 0 && hitEffects != null) {
            hitEffects.onHit(p1, p2, p1.getLastRawDamageReceived());
            if (particleEffects != null) particleEffects.spawnHitSpark(p1.getX(), p1.getY() + 50);
            if (cameraController != null) cameraController.shake(3f + dmg1 * 0.5f, 0.15f);
        }
        if (dmg2 > 0 && hitEffects != null) {
            hitEffects.onHit(p2, p1, p2.getLastRawDamageReceived());
            if (particleEffects != null) particleEffects.spawnHitSpark(p2.getX(), p2.getY() + 50);
            if (cameraController != null) cameraController.shake(3f + dmg2 * 0.5f, 0.15f);
        }

        // 音效
        triggerAudio(p1, p2, dmg1, dmg2);

        // 更新视觉效果
        if (hitEffects != null) hitEffects.update(delta);
        if (particleEffects != null) particleEffects.update(delta);
        if (motionTrailEffect != null) motionTrailEffect.sample(p1, p2, delta);

        cameraController.update(p1, p2, delta);
    }

    private void updateNetwork(float delta) {
        if (gameClient == null) return;

        // 采样本地输入并发送
        int localId = gameClient.getLocalPlayerId();
        InputCommand cmd = inputHandler.samplePlayer1(frameNumber);
        cmd.playerId = localId;
        cmd.frameNumber = frameNumber;
        gameClient.setCurrentLocalInput(cmd);
        gameClient.sendInputToOpponent(cmd);
        frameNumber++;

        // 读取游戏世界（FrameSyncManager 线程写入，此处加读锁）
        GameWorld gw = gameClient.getGameWorldReadLocked();
        try {
            Fighter p1 = gw.getPlayer1();
            Fighter p2 = gw.getPlayer2();
            if (p1 == null || p2 == null) {
                if (hitEffects != null) hitEffects.update(delta);
                if (particleEffects != null) particleEffects.update(delta);
                return;
            }

            // 初始化血量追踪
            if (netHp1Before < 0) {
                netHp1Before = p1.getHealth();
                netHp2Before = p2.getHealth();
            }

            // 命中检测
            int dmg1 = netHp1Before - p1.getHealth();
            int dmg2 = netHp2Before - p2.getHealth();

            if (dmg1 > 0 && hitEffects != null) {
                hitEffects.onHit(p1, p2, p1.getLastRawDamageReceived());
                if (particleEffects != null) particleEffects.spawnHitSpark(p1.getX(), p1.getY() + 50);
                if (cameraController != null) cameraController.shake(3f + dmg1 * 0.5f, 0.15f);
            }
            if (dmg2 > 0 && hitEffects != null) {
                hitEffects.onHit(p2, p1, p2.getLastRawDamageReceived());
                if (particleEffects != null) particleEffects.spawnHitSpark(p2.getX(), p2.getY() + 50);
                if (cameraController != null) cameraController.shake(3f + dmg2 * 0.5f, 0.15f);
            }

            netHp1Before = p1.getHealth();
            netHp2Before = p2.getHealth();

            // 音效
            triggerAudio(p1, p2, dmg1, dmg2);

            // 更新视觉效果
            if (hitEffects != null) hitEffects.update(delta);
            if (particleEffects != null) particleEffects.update(delta);
            if (motionTrailEffect != null) motionTrailEffect.sample(p1, p2, delta);

            cameraController.update(p1, p2, delta);
        } finally {
            gameClient.releaseReadLock();
        }
    }

    // === 音效触发 ===

    private void triggerAudio(Fighter p1, Fighter p2, int dmg1, int dmg2) {
        if (audioManager == null) return;

        // 命中音效
        if (dmg1 > 0) {
            int rawDmg = p1.getLastRawDamageReceived();
            audioManager.playHitSound(rawDmg);
            if (p1.consumeAudioBlockedTrigger()) audioManager.playBlockSound();
        }
        if (dmg2 > 0) {
            int rawDmg = p2.getLastRawDamageReceived();
            audioManager.playHitSound(rawDmg);
            if (p2.consumeAudioBlockedTrigger()) audioManager.playBlockSound();
        }

        // 动作音效
        if (p1.consumeAudioDashTrigger()) audioManager.playDashSound();
        if (p1.consumeAudioSpecialTrigger()) audioManager.playSpecialSound();
        if (p2.consumeAudioDashTrigger()) audioManager.playDashSound();
        if (p2.consumeAudioSpecialTrigger()) audioManager.playSpecialSound();

        // KO 音效（只播放一次）
        GameWorld gw = isNetworkMode && gameClient != null ? gameClient.getGameWorldReadLocked() : gameWorld;
        boolean needUnlock = isNetworkMode && gameClient != null;
        try {
            if (!koPlayed && gw.isGameOver()) {
                audioManager.playKoSound();
                koPlayed = true;
            }
        } finally {
            if (needUnlock) gameClient.releaseReadLock();
        }
    }

    // === 渲染 ===

    /** 由 FyrenGame.render() 每帧调用 */
    public void render() {
        ScreenUtils.clear(0.08f, 0.08f, 0.12f, 1f);

        OrthographicCamera cam = cameraController.getCamera();

        // 网络模式从 GameClient 读取 gameWorld，demo 模式用本地 gameWorld
        GameWorld gw;
        boolean needUnlock = false;
        if (isNetworkMode && gameClient != null) {
            gw = gameClient.getGameWorldReadLocked();
            needUnlock = true;
        } else {
            gw = gameWorld;
        }

        try {
            // 背景层
            drawBackground(cam);

            // 角色渲染
            if (spriteRenderer != null && gw.getPlayer1() != null && gw.getPlayer2() != null) {
                spriteRenderer.begin(cam);
                spriteRenderer.drawFighter(gw.getPlayer1());
                spriteRenderer.drawFighter(gw.getPlayer2());
                spriteRenderer.end();
            }

            // 特效层
            if (motionTrailEffect != null) motionTrailEffect.render(cam);
            if (particleEffects != null) particleEffects.render(cam);
            if (hitEffects != null) hitEffects.render(cam);

            // HUD
            if (hudRenderer != null) hudRenderer.render(gw, cam);
        } finally {
            if (needUnlock) {
                gameClient.releaseReadLock();
            }
        }
    }

    // === 背景渲染 ===

    private void drawBackground(OrthographicCamera cam) {
        bgShapes.setProjectionMatrix(cam.combined);

        // 地面（深灰色区域）
        bgShapes.begin(ShapeRenderer.ShapeType.Filled);
        bgShapes.setColor(0.12f, 0.12f, 0.14f, 1f);
        bgShapes.rect(cam.position.x - 500, 0, 1000, 80);
        bgShapes.end();

        // 地面线
        bgShapes.begin(ShapeRenderer.ShapeType.Line);
        bgShapes.setColor(0.25f, 0.25f, 0.28f, 0.6f);
        float startX = cam.position.x - 500;
        for (int i = 0; i < 25; i++) {
            float x1 = startX + i * 40;
            bgShapes.line(x1, 82, x1 + 18, 80);
        }
        bgShapes.end();

        // 中线（格斗场地分隔）
        bgShapes.begin(ShapeRenderer.ShapeType.Line);
        bgShapes.setColor(0.2f, 0.2f, 0.22f, 0.4f);
        bgShapes.line(480, 50, 480, 540);
        bgShapes.end();
    }

    // === 清理 ===

    public void dispose() {
        bgShapes.dispose();
        if (spriteRenderer != null) spriteRenderer.dispose();
        if (hudRenderer != null) hudRenderer.dispose();
        if (particleEffects != null) particleEffects.dispose();
        if (motionTrailEffect != null) motionTrailEffect.dispose();
    }
}
