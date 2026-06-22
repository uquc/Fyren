package com.Fyren.render.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.Fyren.game.Fighter;
import com.Fyren.game.FighterPreset;
import com.Fyren.game.Fighter.ActionState;
import com.Fyren.game.Fighter.ActionType;
import com.Fyren.game.GameWorld;
import com.Fyren.sync.InputCommand;

import java.util.ArrayList;
import java.util.List;

/**
 * 训练模式 — 单人对假人自由练习，含帧数据显示 + 输入状态显示。
 *
 * 假人（P2）站立不动，被击倒后自动回满血。
 * ESC 返回标题画面。
 */
public class TrainingScreen {

    private final GameWorld gameWorld;
    private final GdxInputHandler inputHandler;
    private final CameraController cameraController;

    private final BackgroundRenderer backgroundRenderer;
    private final SpriteRenderer spriteRenderer;
    private final HudRenderer hudRenderer;
    private final HitEffects hitEffects;
    private final ParticleEffects particleEffects;
    private final MotionTrailEffect motionTrailEffect;

    private final BitmapFont font;
    private final SpriteBatch batch;

    private FighterPreset p1Preset;
    private FighterPreset p2Preset;
    private int frameNumber = 0;
    private boolean koPlayed = false;

    private final Runnable onExit;

    public TrainingScreen(FighterPreset p1Preset, BitmapFont font, Runnable onExit) {
        this.p1Preset = p1Preset;
        this.p2Preset = FighterPreset.KAGE;
        this.font = font;
        this.batch = new SpriteBatch();
        this.onExit = onExit;

        gameWorld = new GameWorld();
        gameWorld.setupPlayers(p1Preset, p2Preset);

        inputHandler = new GdxInputHandler();
        cameraController = new CameraController(960, 540);

        backgroundRenderer = new BackgroundRenderer();
        spriteRenderer = new SpriteRenderer();
        hudRenderer = new HudRenderer();
        hitEffects = new HitEffects();
        particleEffects = new ParticleEffects();
        motionTrailEffect = new MotionTrailEffect();
    }

    public void update(float delta) {
        // ESC 退出
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            onExit.run();
            return;
        }

        // 角色切换
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        FighterPreset switched = null;
        boolean switchP1 = false;
        for (int i = 0; i < 3; i++) {
            int key = Input.Keys.NUM_1 + i;
            if (Gdx.input.isKeyJustPressed(key)) {
                switched = FighterPreset.values()[i];
                switchP1 = !shift;
                break;
            }
        }
        if (switched != null) {
            if (switchP1) {
                p1Preset = switched;
            } else {
                p2Preset = switched;
            }
            gameWorld.setupPlayers(p1Preset, p2Preset);
            frameNumber = 0;
            koPlayed = false;
        }

        // P1 输入
        InputCommand cmd1 = inputHandler.samplePlayer1(frameNumber);
        // P2 假人 — 无输入，原地站立
        InputCommand cmd2 = new InputCommand(frameNumber, 2);

        List<InputCommand> inputs = new ArrayList<>();
        inputs.add(cmd1);
        inputs.add(cmd2);

        Fighter p1 = gameWorld.getPlayer1();
        Fighter p2 = gameWorld.getPlayer2();
        int hp1Before = p1.getHealth();
        int hp2Before = p2.getHealth();

        // Hit stop 跳帧
        if (!hitEffects.isInHitStop()) {
            gameWorld.update(inputs, frameNumber);
            frameNumber++;
        }

        // 假人复活（被击倒后完整重置：血量 + gameOver 标志 + 计时器）
        if (p2.getHealth() <= 0) {
            gameWorld.setupPlayers(p1Preset, p2Preset);
            frameNumber = 0;
            koPlayed = false;
        }

        // 命中反馈
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

        // 视觉效果
        hitEffects.update(delta);
        particleEffects.update(delta);
        motionTrailEffect.sample(p1, p2, delta);
        cameraController.update(p1, p2, delta);
    }

    public void render() {
        ScreenUtils.clear(0.08f, 0.08f, 0.12f, 1f);

        OrthographicCamera cam = cameraController.getCamera();
        Fighter p1 = gameWorld.getPlayer1();
        Fighter p2 = gameWorld.getPlayer2();

        // 背景
        backgroundRenderer.render(cam);

        // 角色
        spriteRenderer.begin(cam);
        spriteRenderer.drawFighter(p1);
        spriteRenderer.drawFighter(p2);
        spriteRenderer.end();

        // 特效
        motionTrailEffect.render(cam);
        particleEffects.render(cam);
        hitEffects.render(cam);

        // HUD（训练模式简化：只显示 P1 血条 + 训练标签）
        hudRenderer.render(gameWorld, cam);

        // 帧数据叠加
        renderFrameData(p1, p2);
    }

    // === 帧数据 & 输入显示 ===

    private void renderFrameData(Fighter p1, Fighter p2) {
        batch.begin();

        font.setColor(1f, 1f, 1f, 0.9f);
        font.getData().setScale(0.75f);

        float x = 10;
        float y = 530;
        float lineH = 18;

        // 标题 + 角色选择提示
        font.setColor(0.7f, 0.7f, 1f, 1f);
        font.draw(batch, "== 训练模式 ==", x, y);
        font.setColor(0.5f, 0.5f, 0.7f, 0.8f);
        font.draw(batch, " [1/2/3]切换P1  [Shift+1/2/3]切换假人", x + 120, y);
        y -= lineH + 4;

        // P1 信息
        font.setColor(1f, 0.95f, 0.7f, 1f);
        font.draw(batch, String.format("P1 [%s] HP:%d/%d",
                p1.getPreset().getDisplayName(), p1.getHealth(), p1.getMaxHealth()), x, y);
        y -= lineH;

        font.setColor(0.85f, 0.85f, 0.85f, 1f);
        font.draw(batch, String.format("  姿态: %s", p1.getStance()), x, y);
        y -= lineH;

        // 动作帧数据
        ActionState state = p1.getActionState();
        ActionType type = p1.getActionType();
        if (state != ActionState.IDLE && type != ActionType.NONE) {
            int[] frames = p1.getFrameData(type);
            int total;
            switch (state) {
                case STARTUP: total = frames[0]; break;
                case ACTIVE: total = frames[1]; break;
                case RECOVERY: total = frames[2]; break;
                default: total = 0;
            }
            int remaining = p1.getActionTimer();
            int elapsed = total - remaining;

            // 帧数据条
            font.draw(batch, String.format("  %s [%s] %d/%d",
                    type, state, elapsed, total), x, y);
            y -= lineH;

            // 进度条
            font.draw(batch, String.format("  启动:%d  判定:%d  收尾:%d",
                    frames[0], frames[1], frames[2]), x, y);
            y -= lineH;
        } else {
            font.draw(batch, "  动作: —", x, y);
            y -= lineH;
        }

        // 特殊资源
        String resInfo = "";
        switch (p1.getPreset()) {
            case KAGE:
                resInfo = String.format("特技冷却: %d", p1.getSpecialCooldownRemaining());
                break;
            case TAKESHI:
                resInfo = String.format("伤害积累: %d/40", p1.getDamageDealtSinceLastSpecial());
                break;
            case GOU:
                resInfo = String.format("受伤积累: %d/50", p1.getDamageTakenSinceLastSpecial());
                break;
        }
        font.draw(batch, "  " + resInfo, x, y);
        y -= lineH;

        // 冲刺资源
        font.draw(batch, String.format("  冲刺: %d/3 (计时:%d)",
                p1.getDashCharges(), p1.getDashRechargeTimer()), x, y);
        y -= lineH + 6;

        // --- 输入状态 ---
        font.setColor(0.7f, 0.7f, 1f, 1f);
        font.draw(batch, "--- 输入 ---", x, y);
        y -= lineH;

        font.setColor(0.85f, 0.85f, 0.85f, 1f);
        boolean up = Gdx.input.isKeyPressed(Input.Keys.W);
        boolean down = Gdx.input.isKeyPressed(Input.Keys.S);
        boolean left = Gdx.input.isKeyPressed(Input.Keys.A);
        boolean right = Gdx.input.isKeyPressed(Input.Keys.D);
        boolean punch = Gdx.input.isKeyPressed(Input.Keys.J);
        boolean kick = Gdx.input.isKeyPressed(Input.Keys.K);
        boolean special = Gdx.input.isKeyPressed(Input.Keys.U);
        boolean block = Gdx.input.isKeyPressed(Input.Keys.L);

        StringBuilder inputStr = new StringBuilder();
        if (up) inputStr.append("↑ ");
        if (down) inputStr.append("↓ ");
        if (left) inputStr.append("← ");
        if (right) inputStr.append("→ ");
        if (punch) inputStr.append("[J]拳 ");
        if (kick) inputStr.append("[K]踢 ");
        if (special) inputStr.append("[U]特 ");
        if (block) inputStr.append("[L]防 ");
        if (inputStr.length() == 0) inputStr.append("—");

        font.draw(batch, "  " + inputStr.toString(), x, y);
        y -= lineH;

        // 假人状态
        font.setColor(0.7f, 0.7f, 0.7f, 0.7f);
        font.draw(batch, String.format("假人[%s]: %s HP:%d",
                p2.getPreset().getDisplayName(), p2.getStance(), p2.getHealth()), x, y);

        font.getData().setScale(1.0f);
        batch.end();
    }

    public void dispose() {
        backgroundRenderer.dispose();
        if (spriteRenderer != null) spriteRenderer.dispose();
        if (hudRenderer != null) hudRenderer.dispose();
        if (particleEffects != null) particleEffects.dispose();
        if (motionTrailEffect != null) motionTrailEffect.dispose();
        if (batch != null) batch.dispose();
    }

    public GameWorld getGameWorld() { return gameWorld; }
}
