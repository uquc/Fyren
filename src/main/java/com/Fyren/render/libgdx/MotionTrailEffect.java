package com.Fyren.render.libgdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.Fyren.game.Fighter;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 运动残影效果 — 缓存前 N 帧角色位置，绘制半透明残影。
 *
 * 仅在高速度移动时激活（冲刺、被击退），站立/慢走时清除残影。
 * 纯 CPU 侧实现，Desktop + WebGL 均可用。
 */
public class MotionTrailEffect {

    private static final int MAX_TRAIL = 6;
    private static final float SAMPLE_INTERVAL = 0.016f; // ~每帧

    private final Deque<TrailSnapshot> p1Trail = new ArrayDeque<>();
    private final Deque<TrailSnapshot> p2Trail = new ArrayDeque<>();
    private float timer = 0f;

    private final ShapeRenderer shapes;

    public MotionTrailEffect() {
        shapes = new ShapeRenderer();
    }

    /** 每帧采样角色位置 */
    public void sample(Fighter p1, Fighter p2, float delta) {
        timer += delta;
        if (timer >= SAMPLE_INTERVAL) {
            timer -= SAMPLE_INTERVAL;
            sampleOne(p1Trail, p1);
            sampleOne(p2Trail, p2);
        }
    }

    private void sampleOne(Deque<TrailSnapshot> trail, Fighter f) {
        // 仅高速移动时记录残影
        boolean moving = f.isDashing() || Math.abs(f.getVelocityX()) > 6f;
        if (moving) {
            trail.addFirst(new TrailSnapshot(f.getX(), f.getY()));
            if (trail.size() > MAX_TRAIL) {
                trail.removeLast();
            }
        } else {
            trail.clear();
        }
    }

    public void render(OrthographicCamera camera) {
        if (p1Trail.isEmpty() && p2Trail.isEmpty()) return;

        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        renderTrail(p1Trail);
        renderTrail(p2Trail);

        shapes.end();
    }

    private void renderTrail(Deque<TrailSnapshot> trail) {
        int i = 0;
        for (TrailSnapshot snap : trail) {
            float alpha = (1f - (float) i / MAX_TRAIL) * 0.25f;
            shapes.setColor(1f, 1f, 1f, alpha);
            shapes.rect(snap.x - 14, snap.y + 12, 28, 55);
            i++;
        }
    }

    public void dispose() {
        shapes.dispose();
    }

    private static class TrailSnapshot {
        final float x, y;
        TrailSnapshot(float x, float y) { this.x = x; this.y = y; }
    }
}
