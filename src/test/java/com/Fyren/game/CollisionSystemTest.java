package com.Fyren.game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 碰撞系统单元测试 — 命中、格挡、相杀、投技破防。
 *
 * 直接设置 Fighter 状态和位置来模拟各种碰撞场景，
 * 避免依赖 GameWorld.update() 的复杂帧逻辑。
 */
public class CollisionSystemTest {

    private CollisionSystem cs;
    private Fighter p1, p2;

    @BeforeEach
    void setUp() {
        cs = new CollisionSystem();
        p1 = new Fighter(1, 200, Fighter.GROUND_Y, FighterPreset.TAKESHI, true);
        p2 = new Fighter(2, 300, Fighter.GROUND_Y, FighterPreset.KAGE, false);
    }

    // ========== 普通命中 ==========

    @Test
    public void attackerInActiveFrameHitsDefender() {
        // P1 在 P2 旁边，P1 处于判定帧
        p1.setPosition(250, Fighter.GROUND_Y);
        p2.setPosition(300, Fighter.GROUND_Y);
        p1.setActionState(Fighter.ActionState.ACTIVE);
        p1.setActionType(Fighter.ActionType.PUNCH);

        int hpBefore = p2.getHealth();
        cs.checkCollisions(p1, p2);

        assertTrue(p2.getHealth() < hpBefore, "P2 应受到伤害");
        assertTrue(p2.isInStun(), "P2 应进入僵直状态");
        assertTrue(p2.isHit(), "P2(受击者) 命中标志应被设置");
    }

    @Test
    public void idleFighterDoesNotHit() {
        p1.setPosition(250, Fighter.GROUND_Y);
        p2.setPosition(300, Fighter.GROUND_Y);
        // p1 保持 IDLE 状态

        int hpBefore = p2.getHealth();
        cs.checkCollisions(p1, p2);

        assertEquals(hpBefore, p2.getHealth(), "IDLE 状态不应造成伤害");
    }

    @Test
    public void startupFrameDoesNotHit() {
        p1.setPosition(250, Fighter.GROUND_Y);
        p2.setPosition(300, Fighter.GROUND_Y);
        p1.setActionState(Fighter.ActionState.STARTUP);
        p1.setActionType(Fighter.ActionType.PUNCH);

        int hpBefore = p2.getHealth();
        cs.checkCollisions(p1, p2);

        assertEquals(hpBefore, p2.getHealth(), "前摇帧不应造成伤害");
    }

    // ========== 格挡 ==========

    @Test
    public void blockingReducesDamage() {
        p1.setPosition(250, Fighter.GROUND_Y);
        p2.setPosition(300, Fighter.GROUND_Y);
        p1.setActionState(Fighter.ActionState.ACTIVE);
        p1.setActionType(Fighter.ActionType.PUNCH);
        p2.setBlocking(true);

        int hpBeforeBlock = p2.getHealth();
        cs.checkCollisions(p1, p2);

        // 格挡减伤（半伤），但仍会进入僵直
        int dmgTaken = hpBeforeBlock - p2.getHealth();
        assertTrue(dmgTaken > 0, "格挡仍应受伤");
        assertTrue(p2.getHealth() > 0, "格挡不应致死");
    }

    // ========== 投技破防 ==========

    @Test
    public void throwBreaksGuard() {
        p1.setPosition(250, Fighter.GROUND_Y);
        p2.setPosition(300, Fighter.GROUND_Y);
        p1.setActionState(Fighter.ActionState.ACTIVE);
        p1.setActionType(Fighter.ActionType.THROW);
        p2.setBlocking(true);

        int hpBefore = p2.getHealth();
        cs.checkCollisions(p1, p2);

        // 投技应造成全额伤害（破防）
        assertTrue(p2.getHealth() < hpBefore, "投技应破防造成伤害");
    }

    // ========== 相杀 ==========

    @Test
    public void clashWhenBothInActiveFrame() {
        p1.setPosition(280, Fighter.GROUND_Y);
        p2.setPosition(300, Fighter.GROUND_Y);
        p1.setActionState(Fighter.ActionState.ACTIVE);
        p1.setActionType(Fighter.ActionType.PUNCH);
        p2.setActionState(Fighter.ActionState.ACTIVE);
        p2.setActionType(Fighter.ActionType.PUNCH);

        int hp1Before = p1.getHealth();
        int hp2Before = p2.getHealth();
        cs.checkCollisions(p1, p2);

        // 相杀：双方各受半伤
        assertTrue(p1.getHealth() < hp1Before, "P1 相杀应受伤");
        assertTrue(p2.getHealth() < hp2Before, "P2 相杀应受伤");
        assertTrue(p1.isInStun(), "P1 相杀后应短僵直");
        assertTrue(p2.isInStun(), "P2 相杀后应短僵直");
    }

    // ========== 角色分离 ==========

    @Test
    public void fightersArePushedApartWhenOverlapping() {
        p1.setPosition(290, Fighter.GROUND_Y);
        p2.setPosition(300, Fighter.GROUND_Y);
        float p1x = p1.getX();
        float p2x = p2.getX();

        cs.checkCollisions(p1, p2);

        // 分离后距离应增加
        float distAfter = Math.abs(p1.getX() - p2.getX());
        float distBefore = Math.abs(p1x - p2x);
        assertTrue(distAfter > distBefore, "重叠角色应被推开");
    }
}
