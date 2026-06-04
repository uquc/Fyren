package com.Fyren.render.libgdx;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.Fyren.game.Fighter;

/**
 * 动态摄像机 — 根据两个角色位置调整视野，支持屏幕震动。
 *
 * 世界坐标系：X 左→右，Y 下→上。屏幕坐标系：libGDX 默认 origin 左下。
 * 视野中心锁定两人中点，缩放幅度根据两人距离动态调节。
 */
public class CameraController {

    private final OrthographicCamera camera;
    private float shakeIntensity = 0f;
    private float shakeDuration = 0f;

    private static final float MIN_ZOOM = 0.6f;
    private static final float MAX_ZOOM = 1.4f;
    private static final float BASE_ZOOM = 1.0f;

    // 世界→屏幕参数（与旧 GamePanel 一致的映射逻辑）
    private static final float MIN_SCREEN_DIST = 200f;
    private static final float MAX_SCREEN_DIST = 700f;

    public CameraController(float viewportWidth, float viewportHeight) {
        camera = new OrthographicCamera(viewportWidth, viewportHeight);
        camera.setToOrtho(false, viewportWidth, viewportHeight);
        camera.position.set(viewportWidth / 2f, viewportHeight / 2f, 0);
    }

    /** 每帧更新摄像机位置和缩放 */
    public void update(Fighter p1, Fighter p2, float delta) {
        float worldCenterX = (p1.getX() + p2.getX()) / 2f;
        float worldDist = Math.abs(p2.getX() - p1.getX());

        // 动态缩放：两人近时放大，远时缩小
        float targetZoom;
        if (worldDist > 500) {
            targetZoom = MathUtils.clamp(MAX_SCREEN_DIST / worldDist, MIN_ZOOM, BASE_ZOOM);
        } else if (worldDist < 200) {
            targetZoom = MathUtils.clamp(MIN_SCREEN_DIST / worldDist, BASE_ZOOM, MAX_ZOOM);
        } else {
            targetZoom = BASE_ZOOM;
        }

        // 平滑过渡（10% lerp per frame）
        camera.zoom += (targetZoom - camera.zoom) * 0.1f;

        // 摄像机中心 = 两人 X 中点
        camera.position.x = worldCenterX;
        camera.position.y = 270f;

        // 屏幕震动
        if (shakeDuration > 0) {
            shakeDuration -= delta;
            float shakeX = (MathUtils.random() - 0.5f) * shakeIntensity * 2f;
            float shakeY = (MathUtils.random() - 0.5f) * shakeIntensity * 2f;
            camera.position.x += shakeX;
            camera.position.y += shakeY;
            if (shakeDuration <= 0) {
                shakeIntensity = 0f;
            }
        }

        camera.update();
    }

    /** 触发屏幕震动。intensity 建议 2-8px，duration 建议 0.08-0.2s */
    public void shake(float intensity, float duration) {
        this.shakeIntensity = Math.max(this.shakeIntensity, intensity);
        this.shakeDuration = Math.max(this.shakeDuration, duration);
    }

    public OrthographicCamera getCamera() {
        return camera;
    }
}
