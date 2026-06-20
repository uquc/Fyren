package com.Fyren.render.libgdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.TimeUtils;

/**
 * 多层视差背景渲染 — 全部程序化生成，无外部素材依赖。
 *
 * 4 层视差（后 → 前）：
 *   1. 天空渐变 + 星点（静止）
 *   2. 远山剪影（0.15x 视差）
 *   3. 中景竹林/树（0.4x 视差）
 *   4. 地面 + 草地纹理（1.0x 跟随摄像机）
 *
 * 山脉顶点在构造时生成，帧间仅平移视差偏移。
 */
public class BackgroundRenderer {

    private final ShapeRenderer shapes;

    // 世界中心（场地中心，480 为默认值）
    private static final float WORLD_CENTER_X = 480f;
    private static final float WORLD_CENTER_Y = 80f;

    // 远山数据（预生成）
    private final float[] farMountainPeaks;   // [x1, y1, x2, y2, ...]
    private final float[] midTreePositions;    // [x1, y1, h1, x2, y2, h2, ...]
    private static final int FAR_MOUNTAINS = 40;
    private static final int MID_TREES = 60;

    // 星星
    private final float[] stars;

    // 颜色常量 — 夜间道场风格
    private static final Color SKY_TOP    = new Color(0.04f, 0.04f, 0.12f, 1f);
    private static final Color SKY_BOT    = new Color(0.12f, 0.08f, 0.18f, 1f);
    private static final Color FAR_MTN    = new Color(0.06f, 0.10f, 0.08f, 1f);
    private static final Color FAR_MTN2   = new Color(0.08f, 0.14f, 0.10f, 1f);
    private static final Color MID_TREE_C = new Color(0.10f, 0.18f, 0.10f, 1f);
    private static final Color MID_BAMBOO = new Color(0.12f, 0.22f, 0.12f, 1f);
    private static final Color GROUND_FILL = new Color(0.10f, 0.10f, 0.12f, 1f);
    private static final Color GROUND_TOP  = new Color(0.18f, 0.24f, 0.14f, 1f);
    private static final Color GRASS_LINE  = new Color(0.15f, 0.22f, 0.12f, 0.7f);
    private static final Color MOON_COLOR  = new Color(0.25f, 0.25f, 0.22f, 0.6f);

    public BackgroundRenderer() {
        this.shapes = new ShapeRenderer();

        // 预生成远山轮廓（正弦叠加 + 随机扰动）
        farMountainPeaks = new float[FAR_MOUNTAINS * 2];
        long seed = 42;
        for (int i = 0; i < FAR_MOUNTAINS; i++) {
            float x = -400 + i * 80f;
            float h = 160 + (float) Math.sin(i * 0.4) * 60 + (float) Math.sin(i * 0.9 + 1.3) * 40
                    + pseudoRandom(seed + i * 7) * 25;
            farMountainPeaks[i * 2] = x;
            farMountainPeaks[i * 2 + 1] = Math.max(100, Math.min(280, h));
        }

        // 预生成中景树/竹子位置
        midTreePositions = new float[MID_TREES * 3];
        seed = 137;
        for (int i = 0; i < MID_TREES; i++) {
            float x = -450 + i * 50f + pseudoRandom(seed + i * 3) * 30;
            float baseY = 76 + pseudoRandom(seed + i * 5) * 12;
            float height = 40 + pseudoRandom(seed + i * 11) * 80;
            midTreePositions[i * 3] = x;
            midTreePositions[i * 3 + 1] = baseY;
            midTreePositions[i * 3 + 2] = height;
        }

        // 天空星点
        stars = new float[80 * 2];
        seed = 271;
        for (int i = 0; i < 80; i++) {
            stars[i * 2] = pseudoRandom(seed + i * 13) * 1400 - 200;
            stars[i * 2 + 1] = 300 + pseudoRandom(seed + i * 29) * 240;
        }
    }

    /** 简单的确定性伪随机（避免依赖 Java Random） */
    private static float pseudoRandom(long seed) {
        long x = (seed * 1103515245L + 12345L) & 0x7fffffff;
        return (float) (x % 1000) / 1000f;
    }

    public void render(OrthographicCamera cam) {
        shapes.setProjectionMatrix(cam.combined);

        float camX = cam.position.x;
        float camY = cam.position.y;

        // === Layer 0: 天空 ===
        drawSky(camX);

        // === Layer 1: 远山 (0.15x 视差) ===
        drawFarMountains(camX, camY);

        // === Layer 2: 中景树/竹 (0.4x 视差) ===
        drawMidTrees(camX, camY);

        // === Layer 3: 地面 (1.0x) ===
        drawGround(camX);
    }

    private void drawSky(float camX) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // 天空渐变（用多条横条近似）
        int strips = 20;
        float stripH = 540f / strips;
        for (int i = 0; i < strips; i++) {
            float t = (float) i / strips;
            shapes.setColor(
                SKY_TOP.r + (SKY_BOT.r - SKY_TOP.r) * t,
                SKY_TOP.g + (SKY_BOT.g - SKY_TOP.g) * t,
                SKY_TOP.b + (SKY_BOT.b - SKY_TOP.b) * t,
                1f
            );
            shapes.rect(camX - 500, i * stripH, 1000, stripH + 1);
        }

        // 月亮
        float moonX = 700;
        float moonY = 440;
        shapes.setColor(MOON_COLOR);
        shapes.circle(moonX, moonY, 24);
        // 月晕
        shapes.setColor(MOON_COLOR.r, MOON_COLOR.g, MOON_COLOR.b, 0.15f);
        shapes.circle(moonX, moonY, 38);

        // 星星
        shapes.setColor(1f, 1f, 0.9f, 0.5f);
        for (int i = 0; i < stars.length; i += 2) {
            float sx = stars[i];
            float sy = stars[i + 1];
            // 只在屏幕范围内画星点
            if (sx > camX - 500 && sx < camX + 500 && sy > 100 && sy < 540) {
                float twinkle = 0.3f + 0.7f * Math.abs((float) Math.sin(TimeUtils.millis() * 0.001f + i));
                shapes.setColor(1f, 1f, 0.9f, 0.3f + twinkle * 0.5f);
                shapes.circle(sx, sy, 1.2f);
            }
        }

        shapes.end();
    }

    private void drawFarMountains(float camX, float camY) {
        float parallax = 0.15f;
        float offsetX = (camX - WORLD_CENTER_X) * parallax;
        float baseY = 80f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // 两层山脉营造深度感
        for (int layer = 0; layer < 2; layer++) {
            Color c = layer == 0 ? FAR_MTN : FAR_MTN2;
            float yOff = layer == 0 ? 0 : -12;
            shapes.setColor(c);

            for (int i = 0; i < FAR_MOUNTAINS - 1; i++) {
                float x1 = farMountainPeaks[i * 2] + offsetX + yOff * 0.3f;
                float y1 = farMountainPeaks[i * 2 + 1] + yOff;
                float x2 = farMountainPeaks[i * 2 + 2] + offsetX + yOff * 0.3f;
                float y2 = farMountainPeaks[i * 2 + 3] + yOff;

                // 跳过屏幕外的山
                if (x2 < camX - 600 || x1 > camX + 600) continue;

                // 山体三角形：峰顶 → 两山间谷底 → 基线
                float midX = (x1 + x2) / 2;
                float valleyY = baseY + (y1 + y2) * 0.15f;
                shapes.triangle(x1, y1, x1, baseY, midX, valleyY);
                shapes.triangle(midX, valleyY, x1, baseY, x2, baseY);
                shapes.triangle(x2, y2, midX, valleyY, x2, baseY);
            }
        }

        shapes.end();
    }

    private void drawMidTrees(float camX, float camY) {
        float parallax = 0.4f;
        float offsetX = (camX - WORLD_CENTER_X) * parallax;

        shapes.begin(ShapeRenderer.ShapeType.Filled);

        for (int i = 0; i < MID_TREES; i++) {
            float x = midTreePositions[i * 3] + offsetX;
            float baseY = midTreePositions[i * 3 + 1];
            float h = midTreePositions[i * 3 + 2];

            if (x < camX - 550 || x > camX + 550) continue;

            boolean isBamboo = (i % 5 == 0);

            if (isBamboo) {
                // 竹竿 + 竹节 + 尖叶
                shapes.setColor(MID_BAMBOO);
                float trunkW = 2.5f;
                shapes.rect(x - trunkW / 2, baseY, trunkW, h);

                // 竹节横线
                shapes.setColor(0.07f, 0.16f, 0.08f, 0.6f);
                int joints = (int) (h / 25);
                for (int j = 1; j <= joints; j++) {
                    float jy = baseY + j * 25;
                    shapes.rect(x - 4, jy, 8, 1.5f);
                }

                // 竹叶（顶部展开）
                shapes.setColor(MID_BAMBOO);
                float leafY = baseY + h;
                shapes.triangle(x, leafY + 8, x - 12, leafY - 4, x - 4, leafY);
                shapes.triangle(x, leafY + 10, x + 10, leafY - 2, x + 4, leafY);
                shapes.triangle(x, leafY + 6, x - 6, leafY - 8, x, leafY - 2);
            } else {
                // 松树：锥形叠层
                shapes.setColor(MID_TREE_C);
                float halfW = h * 0.22f;
                int tiers = Math.max(1, (int) (h / 28));
                float tierH = h / tiers;
                for (int t = 0; t < tiers; t++) {
                    float ty = baseY + t * tierH;
                    float tw = halfW * (1f - t * 0.25f / tiers);
                    shapes.triangle(x, ty + tierH + 6, x - tw, ty, x + tw, ty);
                }
                // 树干
                shapes.setColor(0.12f, 0.08f, 0.05f, 1f);
                shapes.rect(x - 2, baseY - 2, 4, 10);
            }
        }

        shapes.end();
    }

    private void drawGround(float camX) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // 主地面
        shapes.setColor(GROUND_FILL);
        shapes.rect(camX - 500, 0, 1000, 80);

        // 地面顶部草地带
        shapes.setColor(GROUND_TOP);
        shapes.rect(camX - 500, 76, 1000, 8);

        // 地面纹理 — 不规则石块/土块
        shapes.setColor(0.13f, 0.13f, 0.15f, 0.6f);
        long seed = 583;
        for (int i = 0; i < 40; i++) {
            float rx = camX - 480 + pseudoRandom(seed + i * 17) * 960;
            float ry = pseudoRandom(seed + i * 31) * 66;
            float rw = 6 + pseudoRandom(seed + i * 41) * 18;
            float rh = 2 + pseudoRandom(seed + i * 53) * 5;
            shapes.rect(rx, ry, rw, rh);
        }

        shapes.end();

        // === 草地线条纹理 ===
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(GRASS_LINE);

        // 地面顶线
        shapes.line(camX - 500, 80, camX + 500, 80);

        // 草叶短线
        float startX = camX - 500;
        for (int i = 0; i < 50; i++) {
            float x1 = startX + i * 20;
            float y1 = 80;
            float h = 3 + pseudoRandom(i * 73 + 1) * 8;
            float sway = (float) Math.sin(TimeUtils.millis() * 0.002f + i * 0.6f) * 2f;
            shapes.line(x1, y1, x1 + sway, y1 + h);
        }

        shapes.end();

        // === 场地中线 ===
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.2f, 0.2f, 0.22f, 0.3f);
        shapes.line(WORLD_CENTER_X, 50, WORLD_CENTER_X, 540);
        shapes.end();
    }

    public void dispose() {
        shapes.dispose();
    }
}
