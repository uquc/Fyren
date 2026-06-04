package com.Fyren.render.libgdx;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.Fyren.game.Fighter;

/**
 * 打击感效果管理器 — Hit stop（命中停帧）计时。
 *
 * 打击感五件套分工：
 *   1. Hit stop (本类)        — 命中后冻结 render 若干帧
 *   2. Hurt flash             — Fighter.isHitFlag() → SpriteRenderer.drawHurtFlash()
 *   3. Knockback slide        — Fighter 内部 velX 驱动位移
 *   4. Screen shake           — CameraController.shake()
 *   5. Hit spark particles    — ParticleEffects.spawnHitSpark()
 *
 * Hit stop 时长基于伤害量：轻攻击 ~0.05s (3f)，重攻击 ~0.1s (6f)
 */
public class HitEffects {

    private float hitStopRemaining = 0f;

    private static final float HIT_STOP_LIGHT = 0.05f;  // 3 帧
    private static final float HIT_STOP_HEAVY = 0.1f;   // 6 帧

    /** 当 Fighter 被命中时调用。damage > 15 视为重击 */
    public void onHit(Fighter victim, Fighter attacker, int damage) {
        hitStopRemaining = Math.max(hitStopRemaining,
            damage > 15 ? HIT_STOP_HEAVY : HIT_STOP_LIGHT);
    }

    public void update(float delta) {
        if (hitStopRemaining > 0) {
            hitStopRemaining -= delta;
        }
    }

    /** 当前帧是否处于命中停帧（调用方应跳过 GameWorld.update） */
    public boolean isInHitStop() {
        return hitStopRemaining > 0;
    }

    /** 占位 — 本类不直接渲染 */
    public void render(OrthographicCamera camera) {
        // 无渲染输出，效果委托给其他组件
    }
}
