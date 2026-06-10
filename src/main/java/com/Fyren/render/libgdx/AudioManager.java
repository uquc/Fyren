package com.Fyren.render.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;

/**
 * 音效管理 — 使用 libGDX Sound API。
 *
 * Desktop: 加载 assets/sounds/*.wav
 * WebGL:   Gdx.audio 不可用，静默降级
 */
public class AudioManager implements Disposable {

    private final boolean enabled;
    private Sound hitLight, hitHeavy, special, dash, block, ko;

    public AudioManager() {
        boolean ok = false;
        try {
            if (Gdx.audio != null) {
                hitLight  = Gdx.audio.newSound(Gdx.files.internal("assets/sounds/hit_light.wav"));
                hitHeavy  = Gdx.audio.newSound(Gdx.files.internal("assets/sounds/hit_heavy.wav"));
                special   = Gdx.audio.newSound(Gdx.files.internal("assets/sounds/special.wav"));
                dash      = Gdx.audio.newSound(Gdx.files.internal("assets/sounds/dash.wav"));
                block     = Gdx.audio.newSound(Gdx.files.internal("assets/sounds/block.wav"));
                ko        = Gdx.audio.newSound(Gdx.files.internal("assets/sounds/ko.wav"));
                ok = true;
                System.out.println("[AudioManager] 6 个音效已加载");
            }
        } catch (Exception e) {
            System.out.println("[AudioManager] 音效加载失败，静默运行: " + e.getMessage());
        }
        this.enabled = ok;
    }

    /** 命中音效 — damage > 15 为重击，否则轻击 */
    public void playHitSound(int damage) {
        if (!enabled) return;
        try {
            if (damage > 15 && hitHeavy != null) {
                hitHeavy.play(1.0f);
            } else if (hitLight != null) {
                hitLight.play(1.0f);
            }
        } catch (Exception ignored) {}
    }

    /** 特殊技音效 */
    public void playSpecialSound() {
        if (!enabled || special == null) return;
        try { special.play(1.0f); } catch (Exception ignored) {}
    }

    /** 冲刺音效 */
    public void playDashSound() {
        if (!enabled || dash == null) return;
        try { dash.play(1.0f); } catch (Exception ignored) {}
    }

    /** 格挡音效 */
    public void playBlockSound() {
        if (!enabled || block == null) return;
        try { block.play(1.0f); } catch (Exception ignored) {}
    }

    /** KO 音效 */
    public void playKoSound() {
        if (!enabled || ko == null) return;
        try { ko.play(1.0f); } catch (Exception ignored) {}
    }

    @Override
    public void dispose() {
        if (hitLight  != null) hitLight.dispose();
        if (hitHeavy  != null) hitHeavy.dispose();
        if (special   != null) special.dispose();
        if (dash      != null) dash.dispose();
        if (block     != null) block.dispose();
        if (ko        != null) ko.dispose();
    }
}
