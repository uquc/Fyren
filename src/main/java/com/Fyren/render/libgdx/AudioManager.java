package com.Fyren.render.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Disposable;

/**
 * 音效管理 — 使用 libGDX Sound API。
 *
 * Desktop: 支持 WAV/OGG/MP3 音效文件
 * WebGL:   Gdx.audio 不可用，静默降级
 *
 * 当前为骨架实现 — 音效文件需后续添加至 assets/sounds/ 目录。
 */
public class AudioManager implements Disposable {

    private final boolean enabled;

    public AudioManager() {
        boolean audioAvailable;
        try {
            audioAvailable = Gdx.audio != null;
        } catch (Exception e) {
            audioAvailable = false;
        }
        this.enabled = audioAvailable;
        if (!enabled) {
            System.out.println("[AudioManager] 音频不可用，静默运行");
        }
    }

    /** 命中音效 — 根据伤害量选择轻/重 */
    public void playHitSound(int damage) {
        if (!enabled) return;
        // TODO: 加载音效文件后替换
        // if (damage > 15) heavyHitSound.play();
        // else lightHitSound.play();
    }

    /** 特殊技音效 */
    public void playSpecialSound() {
        if (!enabled) return;
        // TODO: specialSound.play();
    }

    /** 冲刺音效 */
    public void playDashSound() {
        if (!enabled) return;
        // TODO: dashSound.play();
    }

    @Override
    public void dispose() {
        // 未来加载的音效在此释放
    }
}
