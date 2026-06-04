package com.Fyren.render.libgdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 简单粒子系统 — 命中火花、冲刺轨迹。
 * 使用 ShapeRenderer 绘制微型几何体，Desktop + WebGL 均可用。
 *
 * 粒子生命周期：出生 → 重力 + 速度 → 透明度衰减 → 死亡
 */
public class ParticleEffects {

    private final ShapeRenderer shapes;
    private final List<Particle> particles = new ArrayList<>();

    public ParticleEffects() {
        shapes = new ShapeRenderer();
    }

    /** 在命中位置生成火花粒子爆发 */
    public void spawnHitSpark(float worldX, float worldY) {
        for (int i = 0; i < 10; i++) {
            float angle = MathUtils.random(0, MathUtils.PI2);
            float speed = MathUtils.random(80f, 240f);
            particles.add(new Particle(
                worldX, worldY,
                MathUtils.cos(angle) * speed,
                MathUtils.sin(angle) * speed,
                MathUtils.random(0.3f, 0.55f),
                MathUtils.randomBoolean() ? Color.YELLOW : Color.ORANGE
            ));
        }
    }

    /** 冲刺起始位置轨迹粒子 */
    public void spawnDashTrail(float x, float y, float facing) {
        for (int i = 0; i < 6; i++) {
            particles.add(new Particle(
                x, y + MathUtils.random(-15f, 15f),
                -facing * MathUtils.random(30f, 70f),
                MathUtils.random(-15f, 15f),
                MathUtils.random(0.2f, 0.35f),
                new Color(0.7f, 0.75f, 1f, 0.6f)
            ));
        }
    }

    public void update(float delta) {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.life -= delta;
            if (p.life <= 0) {
                it.remove();
                continue;
            }
            p.x += p.vx * delta;
            p.y += p.vy * delta;
            p.vy -= 180f * delta; // 轻微重力
        }
    }

    public void render(OrthographicCamera camera) {
        if (particles.isEmpty()) return;

        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Particle p : particles) {
            float alpha = MathUtils.clamp(p.life / p.maxLife, 0f, 1f);
            shapes.setColor(p.color.r, p.color.g, p.color.b, alpha);
            shapes.rect(p.x - 2, p.y - 2, 4, 4);
        }
        shapes.end();
    }

    public void dispose() {
        shapes.dispose();
    }

    // ========== 内部类 ==========

    private static class Particle {
        float x, y, vx, vy;
        float life, maxLife;
        Color color;

        Particle(float x, float y, float vx, float vy, float life, Color color) {
            this.x = x; this.y = y;
            this.vx = vx; this.vy = vy;
            this.life = life; this.maxLife = life;
            this.color = color;
        }
    }
}
