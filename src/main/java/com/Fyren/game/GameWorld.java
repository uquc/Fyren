package com.Fyren.game;

import com.Fyren.sync.InputCommand;
import java.util.*;

/**
 * 游戏世界 — 完全确定性。
 * 管理两个Fighter、碰撞检测、计时器、回合结束判定。
 */
public class GameWorld {
    private Fighter player1;
    private Fighter player2;
    private final CollisionSystem collisionSystem;

    private int currentFrame = 0;
    private final List<GameStateSnapshot> snapshots = new ArrayList<>();
    private static final int SNAPSHOT_INTERVAL = 10;

    // 计时器
    private static final int GAME_DURATION_SECONDS = 99;
    private int timerFrames = GAME_DURATION_SECONDS * 60;

    // 游戏状态
    private boolean gameOver = false;
    private int winnerId = -1; // 0=平局, 1=P1胜, 2=P2胜, -1=进行中

    public GameWorld() {
        this.collisionSystem = new CollisionSystem();
        this.player1 = new Fighter(1, 200, Fighter.GROUND_Y, FighterPreset.TAKESHI, true);
        this.player2 = new Fighter(2, 700, Fighter.GROUND_Y, FighterPreset.TAKESHI, false);
        saveSnapshot();
    }

    /** 初始化角色（匹配成功后调用） */
    public void setupPlayers(FighterPreset p1Preset, FighterPreset p2Preset) {
        this.player1 = new Fighter(1, 200, Fighter.GROUND_Y, p1Preset, true);
        this.player2 = new Fighter(2, 700, Fighter.GROUND_Y, p2Preset, false);
        this.timerFrames = GAME_DURATION_SECONDS * 60;
        this.currentFrame = 0;
        this.gameOver = false;
        this.winnerId = -1;
        this.snapshots.clear();
        saveSnapshot();
    }

    /** 更新游戏状态 — 必须完全确定性 */
    public void update(List<InputCommand> inputs, int frameNumber) {
        if (gameOver) return;

        this.currentFrame = frameNumber;

        timerFrames--;
        if (timerFrames <= 0) {
            timerFrames = 0;
            endGameByTimeout();
            return;
        }

        inputs.sort(Comparator.comparingInt(c -> c.playerId));

        for (InputCommand cmd : inputs) {
            if (cmd.playerId == player1.getId()) {
                player1.update(cmd, this);
            } else if (cmd.playerId == player2.getId()) {
                player2.update(cmd, this);
            }
        }

        collisionSystem.checkCollisions(player1, player2);
        checkDeath();

        if (currentFrame % SNAPSHOT_INTERVAL == 0) {
            saveSnapshot();
        }
    }

    private void checkDeath() {
        if (player1.getHealth() <= 0) {
            winnerId = 2;
            gameOver = true;
            player1.setHealth(0);
        } else if (player2.getHealth() <= 0) {
            winnerId = 1;
            gameOver = true;
            player2.setHealth(0);
        }
    }

    private void endGameByTimeout() {
        gameOver = true;
        int hp1 = player1.getHealth();
        int hp2 = player2.getHealth();
        if (hp1 > hp2) {
            winnerId = 1;
        } else if (hp2 > hp1) {
            winnerId = 2;
        } else {
            winnerId = 0;
        }
    }

    private void saveSnapshot() {
        snapshots.add(new GameStateSnapshot(currentFrame, player1, player2,
                timerFrames, gameOver, winnerId));
        if (snapshots.size() > 100) {
            snapshots.remove(0);
        }
    }

    public void rollbackTo(int frameNumber) {
        GameStateSnapshot target = null;
        for (int i = snapshots.size() - 1; i >= 0; i--) {
            if (snapshots.get(i).getFrameNumber() <= frameNumber) {
                target = snapshots.get(i);
                break;
            }
        }
        if (target != null) {
            target.restore(player1, player2);
            this.currentFrame = frameNumber;
            this.timerFrames = target.getTimerFrames();
            this.gameOver = target.isGameOver();
            this.winnerId = target.getWinnerId();
            snapshots.removeIf(s -> s.getFrameNumber() > frameNumber);
        }
    }

    public void render() {
        // 控制台渲染 — demo模式保留
    }

    // ========== Getters ==========

    public Fighter getPlayer1() { return player1; }
    public Fighter getPlayer2() { return player2; }
    public int getCurrentFrame() { return currentFrame; }
    public int getLocalPlayerId() { return 1; }
    public int getTimerSeconds() { return Math.max(0, timerFrames / 60); }
    public int getTimerFrames() { return timerFrames; }
    public boolean isGameOver() { return gameOver; }
    public int getWinnerId() { return winnerId; }
}
