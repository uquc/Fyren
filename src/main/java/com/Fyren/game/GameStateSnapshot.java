package com.Fyren.game;

/**
 * 游戏状态快照 — 用于回滚网络。
 * 保存某一帧的完整游戏状态，包括计时器、角色动作状态机、特殊技资源。
 */
public class GameStateSnapshot {
    private final int frameNumber;

    // Player 1
    private final float p1x, p1y, p1vx, p1vy;
    private final int p1Health;
    private final Fighter.ActionState p1ActionState;
    private final Fighter.ActionType p1ActionType;
    private final int p1ActionTimer;
    private final boolean p1Blocking, p1Dashing;
    private final int p1StunRemaining, p1DashCharges, p1DashRechargeTimer;
    private final int p1DashDirection, p1DashTimer;
    private final int p1SpecialCD, p1DamageDealt, p1DamageTaken;
    private final boolean p1FacingRight;

    // Player 2
    private final float p2x, p2y, p2vx, p2vy;
    private final int p2Health;
    private final Fighter.ActionState p2ActionState;
    private final Fighter.ActionType p2ActionType;
    private final int p2ActionTimer;
    private final boolean p2Blocking, p2Dashing;
    private final int p2StunRemaining, p2DashCharges, p2DashRechargeTimer;
    private final int p2DashDirection, p2DashTimer;
    private final int p2SpecialCD, p2DamageDealt, p2DamageTaken;
    private final boolean p2FacingRight;

    // World
    private final int timerFrames;
    private final boolean gameOver;
    private final int winnerId;

    public GameStateSnapshot(int frameNumber, Fighter p1, Fighter p2,
                              int timerFrames, boolean gameOver, int winnerId) {
        this.frameNumber = frameNumber;

        p1x = p1.getX(); p1y = p1.getY();
        p1vx = p1.getVelocityX(); p1vy = p1.getVelocityY();
        p1Health = p1.getHealth();
        p1ActionState = p1.getActionState(); p1ActionType = p1.getActionType();
        p1ActionTimer = p1.getActionTimer();
        p1Blocking = p1.isBlocking(); p1Dashing = p1.isDashing();
        p1StunRemaining = p1.getStunRemaining();
        p1DashCharges = p1.getDashCharges(); p1DashRechargeTimer = p1.getDashRechargeTimer();
        p1DashDirection = p1.getDashDirection(); p1DashTimer = p1.getDashTimer();
        p1SpecialCD = p1.getSpecialCooldownRemaining();
        p1DamageDealt = p1.getDamageDealtSinceLastSpecial();
        p1DamageTaken = p1.getDamageTakenSinceLastSpecial();
        p1FacingRight = p1.isFacingRight();

        p2x = p2.getX(); p2y = p2.getY();
        p2vx = p2.getVelocityX(); p2vy = p2.getVelocityY();
        p2Health = p2.getHealth();
        p2ActionState = p2.getActionState(); p2ActionType = p2.getActionType();
        p2ActionTimer = p2.getActionTimer();
        p2Blocking = p2.isBlocking(); p2Dashing = p2.isDashing();
        p2StunRemaining = p2.getStunRemaining();
        p2DashCharges = p2.getDashCharges(); p2DashRechargeTimer = p2.getDashRechargeTimer();
        p2DashDirection = p2.getDashDirection(); p2DashTimer = p2.getDashTimer();
        p2SpecialCD = p2.getSpecialCooldownRemaining();
        p2DamageDealt = p2.getDamageDealtSinceLastSpecial();
        p2DamageTaken = p2.getDamageTakenSinceLastSpecial();
        p2FacingRight = p2.isFacingRight();

        this.timerFrames = timerFrames;
        this.gameOver = gameOver;
        this.winnerId = winnerId;
    }

    public void restore(Fighter p1, Fighter p2) {
        p1.setPosition(p1x, p1y); p1.setVelocity(p1vx, p1vy);
        p1.setHealth(p1Health);
        p1.setActionState(p1ActionState); p1.setActionType(p1ActionType);
        p1.setActionTimer(p1ActionTimer);
        p1.setBlocking(p1Blocking); p1.setDashing(p1Dashing);
        p1.setStunRemaining(p1StunRemaining);
        p1.setDashCharges(p1DashCharges); p1.setDashRechargeTimer(p1DashRechargeTimer);
        p1.setDashDirection(p1DashDirection); p1.setDashTimer(p1DashTimer);
        p1.setSpecialCooldownRemaining(p1SpecialCD);
        p1.setDamageDealtSinceLastSpecial(p1DamageDealt);
        p1.setDamageTakenSinceLastSpecial(p1DamageTaken);
        p1.setFacingRight(p1FacingRight);

        p2.setPosition(p2x, p2y); p2.setVelocity(p2vx, p2vy);
        p2.setHealth(p2Health);
        p2.setActionState(p2ActionState); p2.setActionType(p2ActionType);
        p2.setActionTimer(p2ActionTimer);
        p2.setBlocking(p2Blocking); p2.setDashing(p2Dashing);
        p2.setStunRemaining(p2StunRemaining);
        p2.setDashCharges(p2DashCharges); p2.setDashRechargeTimer(p2DashRechargeTimer);
        p2.setDashDirection(p2DashDirection); p2.setDashTimer(p2DashTimer);
        p2.setSpecialCooldownRemaining(p2SpecialCD);
        p2.setDamageDealtSinceLastSpecial(p2DamageDealt);
        p2.setDamageTakenSinceLastSpecial(p2DamageTaken);
        p2.setFacingRight(p2FacingRight);
    }

    public int getFrameNumber() { return frameNumber; }
    public int getTimerFrames() { return timerFrames; }
    public boolean isGameOver() { return gameOver; }
    public int getWinnerId() { return winnerId; }
}
