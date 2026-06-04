package com.Fyren.render.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.Fyren.sync.InputCommand;

/**
 * libGDX 输入处理器 — 每帧采样键盘状态生成 InputCommand。
 *
 * P1 (左侧玩家): WASD 移动, J=拳 K=脚 U=特殊技
 * P2 (右侧玩家): 方向键 移动, 1=拳 2=脚 3=特殊技
 *
 * 冲刺: ←← 或 →→ 双击（200ms 窗口），与 KeyInputHandler 逻辑一致。
 */
public class GdxInputHandler extends InputAdapter {

    private static final long DASH_TAP_WINDOW_MS = 200;

    // P1 双击检测
    private long p1LastLeftTime = 0;
    private long p1LastRightTime = 0;
    private boolean p1DashForwardPending = false;
    private boolean p1DashBackwardPending = false;

    // P2 双击检测
    private long p2LastLeftTime = 0;
    private long p2LastRightTime = 0;
    private boolean p2DashForwardPending = false;
    private boolean p2DashBackwardPending = false;

    public GdxInputHandler() {
        Gdx.input.setInputProcessor(this);
    }

    // ========== 实时按键事件（用于双击检测） ==========

    @Override
    public boolean keyDown(int keycode) {
        long now = System.currentTimeMillis();

        // P1 双击检测
        switch (keycode) {
            case Input.Keys.A:
                if (now - p1LastLeftTime <= DASH_TAP_WINDOW_MS) p1DashBackwardPending = true;
                p1LastLeftTime = now;
                break;
            case Input.Keys.D:
                if (now - p1LastRightTime <= DASH_TAP_WINDOW_MS) p1DashForwardPending = true;
                p1LastRightTime = now;
                break;
            // P2 双击检测
            case Input.Keys.LEFT:
                if (now - p2LastLeftTime <= DASH_TAP_WINDOW_MS) p2DashBackwardPending = true;
                p2LastLeftTime = now;
                break;
            case Input.Keys.RIGHT:
                if (now - p2LastRightTime <= DASH_TAP_WINDOW_MS) p2DashForwardPending = true;
                p2LastRightTime = now;
                break;
        }
        return false; // 不消费事件，允许后续处理
    }

    // ========== 帧采样（在 render() 中每帧调用） ==========

    /** 采样 P1 当前帧输入 */
    public InputCommand samplePlayer1(int frameNumber) {
        InputCommand cmd = new InputCommand(frameNumber, 1);

        cmd.up    = Gdx.input.isKeyPressed(Input.Keys.W);
        cmd.down  = Gdx.input.isKeyPressed(Input.Keys.S);
        cmd.left  = Gdx.input.isKeyPressed(Input.Keys.A);
        cmd.right = Gdx.input.isKeyPressed(Input.Keys.D);
        cmd.punch = Gdx.input.isKeyJustPressed(Input.Keys.J);
        cmd.kick  = Gdx.input.isKeyJustPressed(Input.Keys.K);
        cmd.special = Gdx.input.isKeyJustPressed(Input.Keys.U);

        cmd.dashBackward = p1DashBackwardPending;
        cmd.dashForward  = p1DashForwardPending;
        p1DashBackwardPending = false;
        p1DashForwardPending = false;

        return cmd;
    }

    /** 采样 P2 当前帧输入 */
    public InputCommand samplePlayer2(int frameNumber) {
        InputCommand cmd = new InputCommand(frameNumber, 2);

        cmd.up    = Gdx.input.isKeyPressed(Input.Keys.UP);
        cmd.down  = Gdx.input.isKeyPressed(Input.Keys.DOWN);
        cmd.left  = Gdx.input.isKeyPressed(Input.Keys.LEFT);
        cmd.right = Gdx.input.isKeyPressed(Input.Keys.RIGHT);
        cmd.punch = Gdx.input.isKeyJustPressed(Input.Keys.NUM_1);
        cmd.kick  = Gdx.input.isKeyJustPressed(Input.Keys.NUM_2);
        cmd.special = Gdx.input.isKeyJustPressed(Input.Keys.NUM_3);

        cmd.dashBackward = p2DashBackwardPending;
        cmd.dashForward  = p2DashForwardPending;
        p2DashBackwardPending = false;
        p2DashForwardPending = false;

        return cmd;
    }
}
