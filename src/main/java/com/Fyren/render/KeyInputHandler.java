package com.Fyren.render;

import com.Fyren.sync.InputCommand;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.BitSet;

/**
 * 键盘输入处理器 — 维护按键状态位掩码，检测双击(←←/→→)触发冲刺。
 *
 * 按键映射: W=投技 S=防御 A=后退 D=前进 J=拳 K=脚 U=特殊技
 */
public class KeyInputHandler implements KeyListener {

    private static final int IDX_UP = 0;
    private static final int IDX_DOWN = 1;
    private static final int IDX_LEFT = 2;
    private static final int IDX_RIGHT = 3;
    private static final int IDX_PUNCH = 4;
    private static final int IDX_KICK = 5;
    private static final int IDX_SPECIAL = 6;

    private final BitSet keys = new BitSet(8);
    private final int playerId;

    // 双击检测
    private static final long DOUBLE_TAP_WINDOW_MS = 200;
    private long lastLeftPressTime = 0;
    private long lastRightPressTime = 0;
    private volatile boolean dashForwardRequested = false;
    private volatile boolean dashBackwardRequested = false;

    public KeyInputHandler(int playerId) {
        this.playerId = playerId;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W: keys.set(IDX_UP); break;
            case KeyEvent.VK_S: keys.set(IDX_DOWN); break;
            case KeyEvent.VK_A:
                keys.set(IDX_LEFT);
                dashBackwardRequested = checkDoubleTap(lastLeftPressTime);
                lastLeftPressTime = System.currentTimeMillis();
                break;
            case KeyEvent.VK_D:
                keys.set(IDX_RIGHT);
                dashForwardRequested = checkDoubleTap(lastRightPressTime);
                lastRightPressTime = System.currentTimeMillis();
                break;
            case KeyEvent.VK_J: keys.set(IDX_PUNCH); break;
            case KeyEvent.VK_K: keys.set(IDX_KICK); break;
            case KeyEvent.VK_U: keys.set(IDX_SPECIAL); break;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W: keys.clear(IDX_UP); break;
            case KeyEvent.VK_S: keys.clear(IDX_DOWN); break;
            case KeyEvent.VK_A: keys.clear(IDX_LEFT); break;
            case KeyEvent.VK_D: keys.clear(IDX_RIGHT); break;
            case KeyEvent.VK_J: keys.clear(IDX_PUNCH); break;
            case KeyEvent.VK_K: keys.clear(IDX_KICK); break;
            case KeyEvent.VK_U: keys.clear(IDX_SPECIAL); break;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) { /* unused */ }

    private boolean checkDoubleTap(long lastPressTime) {
        return (System.currentTimeMillis() - lastPressTime) <= DOUBLE_TAP_WINDOW_MS;
    }

    /** 采样当前按键状态生成 InputCommand。每帧由 Swing Timer 调用。 */
    public InputCommand sample(int frameNumber) {
        InputCommand cmd = new InputCommand(frameNumber, playerId);
        cmd.up = keys.get(IDX_UP);
        cmd.down = keys.get(IDX_DOWN);
        cmd.left = keys.get(IDX_LEFT);
        cmd.right = keys.get(IDX_RIGHT);
        cmd.punch = keys.get(IDX_PUNCH);
        cmd.kick = keys.get(IDX_KICK);
        cmd.special = keys.get(IDX_SPECIAL);
        return cmd;
    }

    public boolean consumeDashForward() {
        boolean v = dashForwardRequested;
        dashForwardRequested = false;
        return v;
    }

    public boolean consumeDashBackward() {
        boolean v = dashBackwardRequested;
        dashBackwardRequested = false;
        return v;
    }
}
