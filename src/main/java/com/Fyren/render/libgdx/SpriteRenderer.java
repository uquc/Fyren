package com.Fyren.render.libgdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.TimeUtils;
import com.Fyren.game.Fighter;
import com.Fyren.game.FighterPreset;
import com.Fyren.game.FighterStance;

/**
 * 程序化几何角色渲染 — 使用 ShapeRenderer 绘制分层角色，根据 FighterStance 变化姿态。
 *
 * 分层（从底到顶）：
 *   1. 地面阴影椭圆
 *   2. 腿部（双线 + 膝关节）
 *   3. 躯干（倒三角）
 *   4. 手臂（双线 + 肘关节）
 *   5. 头部（填充圆 + 高光）
 *   6. 攻击判定框（黄色线框，仅在判定帧）
 *   7. 受击闪烁（白色叠加，仅受击帧）
 */
public class SpriteRenderer {

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;

    // 预设配色
    private static final Color KAGE_COLOR    = new Color(0.2f, 0.2f, 0.5f, 1f);  // 影 — 深蓝
    private static final Color TAKESHI_COLOR = new Color(0.5f, 0.2f, 0.1f, 1f);  // 武 — 棕红
    private static final Color GOU_COLOR     = new Color(0.3f, 0.5f, 0.1f, 1f);  // 刚 — 深绿
    private static final Color SKIN_COLOR    = new Color(0.9f, 0.75f, 0.5f, 1f); // 肤色
    private static final Color LEG_COLOR     = new Color(0.15f, 0.15f, 0.2f, 1f); // 腿部深色

    public SpriteRenderer() {
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
    }

    public void begin(OrthographicCamera camera) {
        batch.setProjectionMatrix(camera.combined);
        shapes.setProjectionMatrix(camera.combined);
    }

    public void end() {
        // SpriteRenderer uses ShapeRenderer primarily, no batch.begin/end needed
    }

    /** 绘制单个角色 */
    public void drawFighter(Fighter f) {
        float x = f.getX();
        float y = f.getY();
        float facing = f.isFacingRight() ? 1f : -1f;
        Color primary = presetColor(f.getPreset());
        FighterStance stance = f.getStance();

        // 待机呼吸动画
        float idleBob = stance == FighterStance.IDLE
                ? (float) Math.sin(TimeUtils.millis() * 0.004) * 1.5f : 0f;
        float animY = y + idleBob;

        drawShadow(x, y);
        drawLegs(x, animY, facing, stance);
        drawTorso(x, animY, facing, primary, stance);
        drawArms(x, animY, facing, primary, stance);
        drawHead(x, animY, facing, primary, stance);

        if (f.isAttacking()) {
            drawAttackBox(x, y, facing, f.getActionType());
        }

        if (f.isHit()) {
            drawHurtFlash(x, y);
        }
    }

    // ========== 分层绘制 ==========

    private void drawShadow(float x, float y) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.3f);
        shapes.ellipse(x - 22, 78, 44, 8);
        shapes.end();
    }

    private void drawLegs(float x, float y, float facing, FighterStance stance) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(LEG_COLOR);

        float hipX = x;
        float hipY = y + 28;
        float footY = 80f;

        float spread;
        float kneeShift; // 膝盖前后偏移

        switch (stance) {
            case KICK:
                // 前腿高踢，后腿微蹲
                float kickFootY = y + 58;
                shapes.rectLine(hipX, hipY, x + 40f * facing, kickFootY, 4);
                shapes.rectLine(hipX, hipY, x - 6f * facing, footY, 4);
                // 踢击腿膝盖
                shapes.setColor(0.25f, 0.25f, 0.3f, 1f);
                shapes.circle(x + 20f * facing, y + 42, 3);
                shapes.circle(x - 3f * facing, y + 10, 3);
                shapes.end();
                return;

            case DASH:
                // 冲刺：身体前倾，后腿蹬地、前腿屈膝
                shapes.rectLine(hipX, hipY, x - 20f * facing, footY, 4);
                shapes.rectLine(hipX, hipY, x + 8f * facing, y + 10, 4);
                shapes.setColor(0.25f, 0.25f, 0.3f, 1f);
                shapes.circle(x - 10f * facing, y + 10, 3);
                shapes.circle(x + 4f * facing, y + 20, 3);
                shapes.end();
                return;

            case WALK_FORWARD:
            case WALK_BACKWARD:
                // 行走：双腿交替
                float walkPhase = (float) Math.sin(TimeUtils.millis() * 0.01);
                spread = 20f;
                kneeShift = walkPhase * 8f;
                shapes.rectLine(hipX, hipY, x - spread * facing + kneeShift, footY, 4);
                shapes.rectLine(hipX, hipY, x + spread * facing - kneeShift, footY, 4);
                shapes.setColor(0.25f, 0.25f, 0.3f, 1f);
                shapes.circle(x - spread * 0.5f * facing + kneeShift * 0.5f, y + 10, 3);
                shapes.circle(x + spread * 0.5f * facing - kneeShift * 0.5f, y + 10, 3);
                shapes.end();
                return;

            case BLOCK:
                // 防御：微蹲，双腿并拢
                shapes.rectLine(hipX, hipY + 4, x - 8f, footY, 4);
                shapes.rectLine(hipX, hipY + 4, x + 8f, footY, 4);
                shapes.setColor(0.25f, 0.25f, 0.3f, 1f);
                shapes.circle(x - 4f, y + 10, 3);
                shapes.circle(x + 4f, y + 10, 3);
                shapes.end();
                return;

            case HURT:
                // 受伤：身体后仰，双腿微曲
                shapes.rectLine(hipX, hipY + 6, x - 12f, footY, 4);
                shapes.rectLine(hipX, hipY + 6, x + 12f, footY, 4);
                shapes.setColor(0.25f, 0.25f, 0.3f, 1f);
                shapes.circle(x - 6f, y + 10, 3);
                shapes.circle(x + 6f, y + 10, 3);
                shapes.end();
                return;

            case SPECIAL:
                // 特殊技：马步，双腿大开
                shapes.rectLine(hipX, hipY - 2, x - 24f, footY, 4);
                shapes.rectLine(hipX, hipY - 2, x + 24f, footY, 4);
                shapes.setColor(0.25f, 0.25f, 0.3f, 1f);
                shapes.circle(x - 12f, y + 10, 3);
                shapes.circle(x + 12f, y + 10, 3);
                shapes.end();
                return;

            default: // IDLE, PUNCH, THROW
                spread = 14f;
                shapes.rectLine(hipX, hipY, x - spread * facing, footY, 4);
                shapes.rectLine(hipX, hipY, x + spread * facing, footY, 4);
                shapes.setColor(0.25f, 0.25f, 0.3f, 1f);
                shapes.circle(x - spread * 0.5f * facing, y + 10, 3);
                shapes.circle(x + spread * 0.5f * facing, y + 10, 3);
                break;
        }

        shapes.end();
    }

    private void drawTorso(float x, float y, float facing, Color color, FighterStance stance) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(color);

        float topY = y + 55;
        float botY = y + 25;
        float shoulderHalf = 16f;
        float waistHalf = 10f;

        // 根据姿态调整躯干倾斜
        float leanX = 0f;
        float leanTop = 0f;
        switch (stance) {
            case PUNCH:
                leanX = 6f * facing;  // 出拳前倾
                leanTop = 4f * facing;
                break;
            case KICK:
                leanX = -4f * facing; // 踢腿后仰
                leanTop = -3f * facing;
                break;
            case DASH:
                leanX = 10f * facing; // 冲刺前倾
                leanTop = 8f * facing;
                break;
            case HURT:
                leanX = -8f * facing; // 受伤后仰
                leanTop = -6f * facing;
                break;
            case SPECIAL:
                // 特殊技保持直立，沉腰
                topY -= 4;
                botY -= 4;
                break;
            case BLOCK:
                // 防御微蜷
                topY -= 2;
                botY -= 2;
                break;
            default:
                break;
        }

        // 倒三角躯干
        shapes.triangle(
            x + leanTop, topY,
            x - waistHalf + leanX, botY,
            x + waistHalf + leanX, botY
        );
        // 加宽肩部横线
        shapes.rectLine(x - shoulderHalf + leanTop, topY, x + shoulderHalf + leanTop, topY, 4);

        shapes.end();
    }

    private void drawArms(float x, float y, float facing, Color color, FighterStance stance) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(SKIN_COLOR);

        float shoulderY = y + 55;
        float backShoulderX = x - 8f;
        float frontShoulderX = x + 8f;

        switch (stance) {
            case PUNCH:
                // 前手直拳伸出，后手护胸
                shapes.rectLine(backShoulderX, shoulderY, backShoulderX - 2f * facing, shoulderY - 24, 3);
                shapes.rectLine(frontShoulderX, shoulderY, frontShoulderX + 50f * facing, shoulderY + 4, 4);
                // 后手拳
                shapes.setColor(1f, 0.85f, 0.7f, 1f);
                shapes.circle(backShoulderX - 2f * facing, shoulderY - 24, 3);
                // 前手拳（伸出的）
                shapes.setColor(1f, 0.85f, 0.7f, 1f);
                shapes.circle(frontShoulderX + 50f * facing, shoulderY + 4, 5);
                shapes.end();
                return;

            case KICK:
                // 踢腿时手臂展开平衡
                shapes.rectLine(backShoulderX, shoulderY, backShoulderX - 18f * facing, shoulderY - 20, 3);
                shapes.rectLine(frontShoulderX, shoulderY, frontShoulderX + 20f * facing, shoulderY - 16, 3);
                shapes.setColor(1f, 0.85f, 0.7f, 1f);
                shapes.circle(backShoulderX - 18f * facing, shoulderY - 20, 3);
                shapes.circle(frontShoulderX + 20f * facing, shoulderY - 16, 3);
                shapes.end();
                return;

            case THROW:
                // 双手前伸抓投
                shapes.rectLine(backShoulderX, shoulderY, backShoulderX + 40f * facing, shoulderY, 4);
                shapes.rectLine(frontShoulderX, shoulderY, frontShoulderX + 44f * facing, shoulderY - 6, 4);
                shapes.setColor(1f, 0.85f, 0.7f, 1f);
                shapes.circle(backShoulderX + 40f * facing, shoulderY, 4);
                shapes.circle(frontShoulderX + 44f * facing, shoulderY - 6, 4);
                shapes.end();
                return;

            case SPECIAL:
                // 双手高举蓄力
                shapes.rectLine(backShoulderX, shoulderY, backShoulderX - 4f * facing, shoulderY + 30, 4);
                shapes.rectLine(frontShoulderX, shoulderY, frontShoulderX + 4f * facing, shoulderY + 28, 4);
                shapes.setColor(1f, 0.85f, 0.7f, 1f);
                shapes.circle(backShoulderX - 4f * facing, shoulderY + 30, 4);
                shapes.circle(frontShoulderX + 4f * facing, shoulderY + 28, 4);
                shapes.end();
                return;

            case BLOCK:
                // 双手交叉护在身前
                shapes.rectLine(backShoulderX, shoulderY, x + 12f * facing, shoulderY - 16, 3);
                shapes.rectLine(frontShoulderX, shoulderY, x + 10f * facing, shoulderY - 20, 3);
                shapes.setColor(1f, 0.85f, 0.7f, 1f);
                shapes.circle(x + 12f * facing, shoulderY - 16, 3);
                shapes.circle(x + 10f * facing, shoulderY - 20, 3);
                shapes.end();
                return;

            case HURT:
                // 受伤手臂下垂
                shapes.rectLine(backShoulderX, shoulderY, backShoulderX - 4f * facing, shoulderY - 30, 3);
                shapes.rectLine(frontShoulderX, shoulderY, frontShoulderX + 4f * facing, shoulderY - 28, 3);
                shapes.setColor(1f, 0.85f, 0.7f, 1f);
                shapes.circle(backShoulderX - 4f * facing, shoulderY - 30, 3);
                shapes.circle(frontShoulderX + 4f * facing, shoulderY - 28, 3);
                shapes.end();
                return;

            case DASH:
                // 冲刺手臂后摆
                shapes.rectLine(backShoulderX, shoulderY, backShoulderX - 8f * facing, shoulderY - 20, 3);
                shapes.rectLine(frontShoulderX, shoulderY, frontShoulderX - 6f * facing, shoulderY - 18, 3);
                shapes.setColor(1f, 0.85f, 0.7f, 1f);
                shapes.circle(backShoulderX - 8f * facing, shoulderY - 20, 3);
                shapes.circle(frontShoulderX - 6f * facing, shoulderY - 18, 3);
                shapes.end();
                return;

            default: // IDLE, WALK
                break;
        }

        // 默认/Idle/Walk 姿态的手臂
        float elbowJointY = shoulderY - 14;
        float handY = elbowJointY - 14;

        // 行走时手臂摆动
        float armSwing = 0f;
        if (stance == FighterStance.WALK_FORWARD || stance == FighterStance.WALK_BACKWARD) {
            armSwing = (float) Math.sin(TimeUtils.millis() * 0.01) * 6f;
        }

        // 后手（内侧）
        shapes.rectLine(backShoulderX, shoulderY, backShoulderX + (4f + armSwing) * facing, handY, 3);

        // 前手（外侧）
        shapes.rectLine(frontShoulderX, shoulderY, frontShoulderX + (10f - armSwing) * facing, elbowJointY, 3);
        shapes.rectLine(frontShoulderX + (10f - armSwing) * facing, elbowJointY,
                frontShoulderX + (14f - armSwing) * facing, handY, 3);

        // 拳头
        shapes.setColor(1f, 0.85f, 0.7f, 1f);
        shapes.circle(frontShoulderX + (14f - armSwing) * facing, handY, 4);

        shapes.end();
    }

    private void drawHead(float x, float y, float facing, Color color, FighterStance stance) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        float headY = y + 68;
        float headX = x;
        float radius = 11f;

        // 根据姿态微调头部位置
        switch (stance) {
            case PUNCH:
                headX += 5f * facing;
                headY -= 2;
                break;
            case KICK:
                headX -= 4f * facing;
                break;
            case DASH:
                headX += 12f * facing;
                headY -= 4;
                break;
            case HURT:
                headX -= 7f * facing;
                break;
            case SPECIAL:
                headY -= 6;
                break;
            case BLOCK:
                headY -= 4;
                headX -= 2f * facing;
                break;
            default:
                break;
        }

        // 头部底色
        shapes.setColor(color);
        shapes.circle(headX, headY, radius);

        // 高光
        shapes.setColor(1f, 1f, 1f, 0.25f);
        shapes.circle(headX + 3, headY + 3, radius * 0.35f);

        shapes.end();
    }

    private void drawAttackBox(float x, float y, float facing, Fighter.ActionType type) {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(1f, 1f, 0f, 0.7f);

        float boxW = type == Fighter.ActionType.KICK ? 40f : 28f;
        float boxH = type == Fighter.ActionType.KICK ? 14f : 18f;
        float boxX = x + 28f * facing;
        float boxY = y + 38f;

        shapes.rect(boxX, boxY, boxW, boxH);
        shapes.end();
    }

    private void drawHurtFlash(float x, float y) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(1f, 1f, 1f, 0.45f);
        shapes.rect(x - 26, y + 5, 52, 70);
        shapes.end();
    }

    // ========== 工具方法 ==========

    private Color presetColor(FighterPreset preset) {
        switch (preset) {
            case KAGE:    return KAGE_COLOR;
            case TAKESHI: return TAKESHI_COLOR;
            case GOU:     return GOU_COLOR;
            default:      return Color.GRAY;
        }
    }

    public void dispose() {
        batch.dispose();
        shapes.dispose();
    }
}
