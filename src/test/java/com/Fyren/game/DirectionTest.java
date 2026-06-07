package com.Fyren.game;

import com.Fyren.sync.InputCommand;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证面相相对方向系统（BUG修复验证）。
 *
 * 核心规则：left=后退(面朝反方向), right=前进(面朝方向)。
 * 角色自动面朝对手，所以：
 *   P1(左,面朝右): right→右移(向前), left→左移(向后)
 *   P2(右,面朝左): right→左移(向前), left→右移(向后)
 */
public class DirectionTest {

    // ========== P1 方向测试 ==========

    @Test
    public void p1RightIsForward() {
        GameWorld world = new GameWorld();
        world.setupPlayers(FighterPreset.TAKESHI, FighterPreset.TAKESHI);

        Fighter p1 = world.getPlayer1();
        float startX = p1.getX(); // P1 初始 x=200, 面朝右

        // P1 按 right(前进) — 应向右移动
        InputCommand cmd = forwardCmd(1, 0);
        world.update(inputs(cmd, emptyCmd(2, 0)), 0);

        assertTrue(p1.getX() > startX,
            "P1(面朝右) 按 right 应向右移动(前进)。startX=" + startX + ", now=" + p1.getX());
        assertTrue(p1.isFacingRight(), "P1 应保持面朝右");
    }

    @Test
    public void p1LeftIsBackward() {
        GameWorld world = new GameWorld();
        world.setupPlayers(FighterPreset.TAKESHI, FighterPreset.TAKESHI);

        Fighter p1 = world.getPlayer1();
        float startX = p1.getX(); // x=200

        // P1 按 left(后退) — 应向左移动(远离对手)
        InputCommand cmd = backwardCmd(1, 0);
        world.update(inputs(cmd, emptyCmd(2, 0)), 0);

        assertTrue(p1.getX() < startX,
            "P1(面朝右) 按 left 应向左移动(后退)。startX=" + startX + ", now=" + p1.getX());
    }

    // ========== P2 方向测试（关键：P2 在右侧面朝左） ==========

    @Test
    public void p2RightIsForwardTowardP1() {
        GameWorld world = new GameWorld();
        world.setupPlayers(FighterPreset.TAKESHI, FighterPreset.TAKESHI);

        Fighter p2 = world.getPlayer2();
        float startX = p2.getX(); // P2 初始 x=700, 面朝左(朝向P1)

        assertFalse(p2.isFacingRight(), "P2 应面朝左");

        // P2 按 right(前进) — 面朝左，前进=左移(靠近P1)
        InputCommand cmd = forwardCmd(2, 0);
        world.update(inputs(emptyCmd(1, 0), cmd), 0);

        assertTrue(p2.getX() < startX,
            "P2(面朝左) 按 right(前进) 应向左移靠近P1。startX=" + startX + ", now=" + p2.getX());
    }

    @Test
    public void p2LeftIsBackwardAwayFromP1() {
        GameWorld world = new GameWorld();
        world.setupPlayers(FighterPreset.TAKESHI, FighterPreset.TAKESHI);

        Fighter p2 = world.getPlayer2();
        float startX = p2.getX(); // x=700

        // P2 按 left(后退) — 面朝左，后退=右移(远离P1)
        InputCommand cmd = backwardCmd(2, 0);
        world.update(inputs(emptyCmd(1, 0), cmd), 0);

        assertTrue(p2.getX() > startX,
            "P2(面朝左) 按 left(后退) 应向右移远离P1。startX=" + startX + ", now=" + p2.getX());
    }

    // ========== 自动面朝对手 ==========

    @Test
    public void autoFacingTracksOpponent() {
        GameWorld world = new GameWorld();
        world.setupPlayers(FighterPreset.TAKESHI, FighterPreset.TAKESHI);

        Fighter p1 = world.getPlayer1();
        Fighter p2 = world.getPlayer2();

        // 初始: P1在左面朝右, P2在右面朝左
        assertTrue(p1.isFacingRight(), "P1(左)应面朝右");
        assertFalse(p2.isFacingRight(), "P2(右)应面朝左");

        // 直接交换位置（碰撞推挤会阻止角色穿过对方，无法通过走路跨过）
        p1.setPosition(700, Fighter.GROUND_Y);
        p2.setPosition(200, Fighter.GROUND_Y);

        // 跑一帧让 auto-face 生效
        world.update(inputs(emptyCmd(1, 0), emptyCmd(2, 0)), 0);

        // P1 现在在右 → 应面朝左，P2 在左 → 应面朝右
        assertFalse(p1.isFacingRight(), "P1(在右)应自动面朝左");
        assertTrue(p2.isFacingRight(), "P2(在左)应自动面朝右");
    }

    // ========== 冲刺方向（face-relative） ==========

    @Test
    public void p2DashForwardIsTowardP1() {
        GameWorld world = new GameWorld();
        world.setupPlayers(FighterPreset.TAKESHI, FighterPreset.TAKESHI);

        Fighter p2 = world.getPlayer2();
        float startX = p2.getX(); // x=700

        // P2 向前冲刺 — 第0帧发起，第1帧开始位移
        for (int i = 0; i < 3; i++) {
            world.update(inputs(emptyCmd(1, i), dashForwardCmd(2, i)), i);
        }

        assertTrue(p2.getX() < startX,
            "P2(面朝左) dashForward 应向左冲。startX=" + startX + ", now=" + p2.getX());
    }

    @Test
    public void p2DashBackwardIsAwayFromP1() {
        GameWorld world = new GameWorld();
        world.setupPlayers(FighterPreset.TAKESHI, FighterPreset.TAKESHI);

        Fighter p2 = world.getPlayer2();
        float startX = p2.getX(); // x=700

        // P2 向后冲刺 — 第0帧发起，第1帧开始位移
        for (int i = 0; i < 3; i++) {
            world.update(inputs(emptyCmd(1, i), dashBackwardCmd(2, i)), i);
        }

        assertTrue(p2.getX() > startX,
            "P2(面朝左) dashBackward 应向右冲。startX=" + startX + ", now=" + p2.getX());
    }

    // ========== 攻击框方向 ==========

    @Test
    public void attackBoxFacesOpponent() {
        GameWorld world = new GameWorld();
        world.setupPlayers(FighterPreset.TAKESHI, FighterPreset.TAKESHI);

        Fighter p2 = world.getPlayer2(); // x=700, 面朝左
        Fighter p1 = world.getPlayer1(); // x=200, 面朝右

        // 让 P2 出拳(需要经过前摇帧进入判定帧)
        // PUNCH: startup=3, active=3, recovery=5
        InputCommand punchCmd = punchCmd(2, 0);
        for (int frame = 0; frame < 4; frame++) {
            world.update(inputs(emptyCmd(1, frame), punchCmd), frame);
            // 前几帧保持按键让动作不中断（IDLE后每帧重新检测cmd）
            if (frame >= 1) {
                punchCmd = punchCmd(2, frame); // 新帧的指令
            }
        }

        java.awt.Rectangle atkBox = p2.getAttackBox();
        // P2 在 x=700 面朝左，攻击框应在左(小x)侧延伸
        assertTrue(atkBox.width > 0, "P2 应在判定帧有攻击框");
        assertTrue(atkBox.getCenterX() < p2.getX(),
            "P2(面朝左) 攻击框中心应在角色左侧。atkBox.x=" + atkBox.x + ", p2.x=" + p2.getX());
    }

    // ========== 辅助方法 ==========

    /** 将输入列表变为可变列表（GameWorld.update 内部会 sort） */
    @SafeVarargs
    private static java.util.List<InputCommand> inputs(InputCommand... cmds) {
        return new java.util.ArrayList<>(java.util.Arrays.asList(cmds));
    }
    private static InputCommand emptyCmd(int playerId, int frame) {
        return new InputCommand(frame, playerId);
    }

    /** 仅前进(right=true) */
    private static InputCommand forwardCmd(int playerId, int frame) {
        InputCommand cmd = new InputCommand(frame, playerId);
        cmd.right = true;
        return cmd;
    }

    /** 仅后退(left=true) */
    private static InputCommand backwardCmd(int playerId, int frame) {
        InputCommand cmd = new InputCommand(frame, playerId);
        cmd.left = true;
        return cmd;
    }

    /** 仅拳 */
    private static InputCommand punchCmd(int playerId, int frame) {
        InputCommand cmd = new InputCommand(frame, playerId);
        cmd.punch = true;
        return cmd;
    }

    /** 仅向前冲刺 */
    private static InputCommand dashForwardCmd(int playerId, int frame) {
        InputCommand cmd = new InputCommand(frame, playerId);
        cmd.dashForward = true;
        return cmd;
    }

    /** 仅向后冲刺 */
    private static InputCommand dashBackwardCmd(int playerId, int frame) {
        InputCommand cmd = new InputCommand(frame, playerId);
        cmd.dashBackward = true;
        return cmd;
    }
}
