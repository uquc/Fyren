package com.Fyren.game;

import com.Fyren.sync.InputCommand;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证训练模式 BUG：假人被击倒后 gameOver 未重置，导致后续帧冻结。
 *
 * 复现步骤：
 *   1. P1 击倒 P2 → GameWorld.update() 设 gameOver=true
 *   2. TrainingScreen 恢复 P2 血量（setHealth）但未重置 gameOver
 *   3. 下一帧 GameWorld.update() 因 gameOver 直接 return → 角色定格
 */
public class TrainingModeFreezeTest {

    @Test
    public void reviveP2DoesNotUnfreezeGameWorld() {
        GameWorld world = new GameWorld();
        world.setupPlayers(FighterPreset.TAKESHI, FighterPreset.KAGE);

        Fighter p1 = world.getPlayer1();
        Fighter p2 = world.getPlayer2();
        float p1StartX = p1.getX();

        // Step 1: KO P2
        p2.setHealth(0);
        world.update(inputs(emptyCmd(1, 0), emptyCmd(2, 0)), 0);

        assertTrue(world.isGameOver(), "P2 被 KO 后 gameOver 应为 true");

        // Step 2: 模拟 TrainingScreen 复活假人（只恢复血量，不重置 gameOver）
        p2.setHealth(p2.getMaxHealth());
        assertTrue(p2.getHealth() > 0, "P2 已复活，血量应 > 0");
        assertTrue(world.isGameOver(), "gameOver 仍然为 true（未被重置）");

        // Step 3: 下一帧 P1 尝试移动
        InputCommand p1Right = new InputCommand(1, 1);
        p1Right.right = true;
        world.update(inputs(p1Right, emptyCmd(2, 1)), 1);

        // BUG #28: GameWorld.update() 在 gameOver 时直接 return，
        // P1 输入被忽略，角色定格。
        assertEquals(p1StartX, p1.getX(), 0.01f,
            "BUG #28: 假人复活后 P1 无法移动 — gameOver=true 导致 update() 直接返回");
    }

    @Test
    public void setupPlayersResetsGameOver() {
        // 验证正确的修复方式：重新调用 setupPlayers() 可以重置 gameOver
        GameWorld world = new GameWorld();
        world.setupPlayers(FighterPreset.TAKESHI, FighterPreset.KAGE);

        Fighter p2 = world.getPlayer2();

        // KO P2
        p2.setHealth(0);
        world.update(inputs(emptyCmd(1, 0), emptyCmd(2, 0)), 0);
        assertTrue(world.isGameOver());

        // 正确修复：重新 setupPlayers 重置整个 gameWorld
        world.setupPlayers(FighterPreset.TAKESHI, FighterPreset.KAGE);

        assertFalse(world.isGameOver(), "setupPlayers() 应重置 gameOver 为 false");
        assertTrue(world.getPlayer2().getHealth() > 0, "新 P2 血量应 > 0");
    }

    // === 辅助方法（与 DirectionTest 保持一致） ===

    @SafeVarargs
    private static java.util.List<InputCommand> inputs(InputCommand... cmds) {
        return new java.util.ArrayList<>(java.util.Arrays.asList(cmds));
    }

    private static InputCommand emptyCmd(int playerId, int frame) {
        return new InputCommand(frame, playerId);
    }
}
