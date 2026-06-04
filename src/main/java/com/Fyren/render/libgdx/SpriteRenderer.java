package com.Fyren.render.libgdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.Fyren.game.Fighter;
import com.Fyren.game.FighterPreset;

/**
 * 程序化几何角色渲染 — 使用 ShapeRenderer 绘制分层角色。
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

        drawShadow(x, y);
        drawLegs(x, y, facing);
        drawTorso(x, y, primary);
        drawArms(x, y, facing);
        drawHead(x, y, primary);

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

    private void drawLegs(float x, float y, float facing) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(LEG_COLOR);

        float hipX = x;
        float hipY = y + 28;
        float kneeY = y + 10;
        float footY = 80f;
        float spread = 14f;

        // 左腿（后侧）
        shapes.rectLine(hipX, hipY, x - spread * facing, footY, 4);
        // 右腿（前侧）
        shapes.rectLine(hipX, hipY, x + spread * facing, footY, 4);

        // 膝盖关节
        shapes.setColor(0.25f, 0.25f, 0.3f, 1f);
        shapes.circle(x - spread * 0.5f * facing, kneeY, 3);
        shapes.circle(x + spread * 0.5f * facing, kneeY, 3);

        shapes.end();
    }

    private void drawTorso(float x, float y, Color color) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(color);

        float topY = y + 55;
        float botY = y + 25;
        float shoulderHalf = 16f;
        float waistHalf = 10f;

        // 倒三角躯干
        shapes.triangle(
            x, topY,
            x - waistHalf, botY,
            x + waistHalf, botY
        );
        // 加宽肩部横线
        shapes.rectLine(x - shoulderHalf, topY, x + shoulderHalf, topY, 4);

        shapes.end();
    }

    private void drawArms(float x, float y, float facing) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(SKIN_COLOR);

        float shoulderY = y + 55;
        float elbowJointY = shoulderY - 14;
        float handY = elbowJointY - 14;

        // 后手（内侧）
        float backShoulderX = x - 8f;
        shapes.rectLine(backShoulderX, shoulderY, backShoulderX + 4f * facing, handY, 3);

        // 前手（外侧）
        float frontShoulderX = x + 8f;
        shapes.rectLine(frontShoulderX, shoulderY, frontShoulderX + 10f * facing, elbowJointY, 3);
        shapes.rectLine(frontShoulderX + 10f * facing, elbowJointY, frontShoulderX + 14f * facing, handY, 3);

        // 拳头
        shapes.setColor(1f, 0.85f, 0.7f, 1f);
        shapes.circle(frontShoulderX + 14f * facing, handY, 4);

        shapes.end();
    }

    private void drawHead(float x, float y, Color color) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        float headY = y + 68;
        float radius = 11f;

        // 头部底色
        shapes.setColor(color);
        shapes.circle(x, headY, radius);

        // 高光
        shapes.setColor(1f, 1f, 1f, 0.25f);
        shapes.circle(x + 3, headY + 3, radius * 0.35f);

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
