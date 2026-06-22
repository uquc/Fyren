package com.Fyren.game;

import com.Fyren.sync.InputCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Fighter 动作状态机测试 — STARTUP→ACTIVE→RECOVERY→IDLE 转换。
 */
public class FighterActionTest {

    private GameWorld world;
    private Fighter p1;

    @BeforeEach
    void setUp() {
        world = new GameWorld();
        world.setupPlayers(FighterPreset.TAKESHI, FighterPreset.KAGE);
        p1 = world.getPlayer1();
    }

    // ========== 状态转换 ==========

    @Test
    public void punchEntersStartup() {
        InputCommand cmd = punchCmd(1, 0);
        world.update(inputs(cmd, emptyCmd(2, 0)), 0);

        assertEquals(Fighter.ActionState.STARTUP, p1.getActionState());
        assertEquals(Fighter.ActionType.PUNCH, p1.getActionType());
        assertTrue(p1.getActionTimer() > 0, "前摇帧计时器应 > 0");
    }

    @Test
    public void punchProgressesToActive() {
        // PUNCH: startup=3, active=3, recovery=5
        // startAction 在当前帧不触发 updateAction，计时从下一帧开始
        // 帧 0: IDLE→STARTUP(timer=3), 帧 1-2: STARTUP, 帧 3: ACTIVE
        InputCommand cmd = punchCmd(1, 0);
        for (int f = 0; f < 4; f++) {
            world.update(inputs(cmd, emptyCmd(2, f)), f);
        }

        assertEquals(Fighter.ActionState.ACTIVE, p1.getActionState(),
            "帧 3 应进入判定帧");
    }

    @Test
    public void punchProgressesToRecovery() {
        // 帧 3-5 ACTIVE, 帧 6 RECOVERY
        InputCommand cmd = punchCmd(1, 0);
        for (int f = 0; f < 7; f++) {
            world.update(inputs(cmd, emptyCmd(2, f)), f);
        }

        assertEquals(Fighter.ActionState.RECOVERY, p1.getActionState(),
            "帧 6 应进入收尾帧");
    }

    @Test
    public void punchReturnsToIdle() {
        // startup=3 + active=3 + recovery=5 = 11 帧（startAction 帧不计入）
        // 帧 10: RECOVERY→IDLE
        InputCommand cmd = punchCmd(1, 0);
        for (int f = 0; f < 12; f++) {
            world.update(inputs(cmd, emptyCmd(2, f)), f);
        }

        assertEquals(Fighter.ActionState.IDLE, p1.getActionState());
        assertEquals(Fighter.ActionType.NONE, p1.getActionType());
    }

    // ========== STUN 中断 ==========

    @Test
    public void stunInterruptsAction() {
        // 先进入 STARTUP
        InputCommand cmd = punchCmd(1, 0);
        world.update(inputs(cmd, emptyCmd(2, 0)), 0);
        assertEquals(Fighter.ActionState.STARTUP, p1.getActionState());

        // 直接设 STUN
        p1.takeDamage(20);
        assertEquals(Fighter.ActionState.STUN, p1.getActionState(),
            "受击应中断当前动作进入 STUN");
        assertEquals(Fighter.ActionType.NONE, p1.getActionType());
    }

    // ========== 不同招式 ==========

    @Test
    public void kickHasCorrectStartup() {
        // KICK: startup=5
        InputCommand cmd = new InputCommand(0, 1);
        cmd.kick = true;
        world.update(inputs(cmd, emptyCmd(2, 0)), 0);

        assertEquals(Fighter.ActionType.KICK, p1.getActionType());
        assertTrue(p1.getActionTimer() > 0);
    }

    @Test
    public void specialRequiresResource() {
        // TAKESHI 特殊技需要 40 伤害积累
        // 初始为 0，应无法发动
        InputCommand cmd = new InputCommand(0, 1);
        cmd.special = true;
        world.update(inputs(cmd, emptyCmd(2, 0)), 0);

        // 资源不足时应留在 IDLE
        assertEquals(Fighter.ActionState.IDLE, p1.getActionState());
    }

    @Test
    public void specialFiresWhenResourceReady() {
        // 手动给 TAKESHI 充能
        p1.setDamageDealtSinceLastSpecial(40);
        InputCommand cmd = new InputCommand(0, 1);
        cmd.special = true;
        world.update(inputs(cmd, emptyCmd(2, 0)), 0);

        assertEquals(Fighter.ActionState.STARTUP, p1.getActionState());
        assertEquals(Fighter.ActionType.SPECIAL, p1.getActionType());
    }

    // ========== 防御 ==========

    @Test
    public void downKeyTriggersBlocking() {
        // 防御 = 下方向键 (cmd.down=true)
        InputCommand cmd = new InputCommand(0, 1);
        cmd.down = true;
        world.update(inputs(cmd, emptyCmd(2, 0)), 0);

        assertTrue(p1.isBlocking(), "按 down 应进入防御");
    }

    @Test
    public void idleWithNoInputNotBlocking() {
        world.update(inputs(emptyCmd(1, 0), emptyCmd(2, 0)), 0);
        assertFalse(p1.isBlocking(), "无输入不应防御");
    }

    // ========== 辅助方法 ==========

    @SafeVarargs
    private static java.util.List<InputCommand> inputs(InputCommand... cmds) {
        return new java.util.ArrayList<>(java.util.Arrays.asList(cmds));
    }

    private static InputCommand emptyCmd(int playerId, int frame) {
        return new InputCommand(frame, playerId);
    }

    private static InputCommand punchCmd(int playerId, int frame) {
        InputCommand cmd = new InputCommand(frame, playerId);
        cmd.punch = true;
        return cmd;
    }
}
