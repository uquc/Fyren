package com.Fyren.game;

import com.Fyren.sync.InputCommand;

/**
 * 格斗角色 — 管理位置、血量、动作状态机、特殊技资源、冲刺系统。
 *
 * 动作状态机: IDLE → (前摇 → 判定 → 后摇) → IDLE
 * 僵直会打断任何动作阶段。
 */
public class Fighter {
    // --- 标识与位置 ---
    private final int id;
    private final FighterPreset preset;
    private float x;
    private float y;
    private float velocityX;
    private float velocityY;
    private int health;
    private boolean facingRight; // true=朝右

    // --- 地面常量 ---
    public static final float GROUND_Y = 100f;

    // --- 动作状态机 ---
    public enum ActionState { IDLE, STARTUP, ACTIVE, RECOVERY, STUN, DASH }
    public enum ActionType { NONE, PUNCH, KICK, THROW, SPECIAL }

    private ActionState actionState = ActionState.IDLE;
    private ActionType actionType = ActionType.NONE;
    private int actionTimer = 0;        // 当前阶段剩余帧数
    private boolean isBlocking = false;
    private boolean isAttacking = false; // 判定帧期间为true
    private boolean isHitFlag = false;   // 本帧是否被命中

    // --- 音效触发标志（由 GameScreen 消费并清除） ---
    private boolean audioDashTrigger = false;
    private boolean audioSpecialTrigger = false;
    private boolean audioBlockedTrigger = false;

    // --- 僵直 ---
    private int stunRemaining = 0;
    int lastRawDamageReceived = 0; // 本帧受到的原始伤害（防御减免前），供 hit-stop 判断用（包内可见）

    // --- 冲刺 ---
    private static final int MAX_DASH_CHARGES = 3;
    private static final int DASH_RECHARGE_FRAMES = 180; // 3秒@60fps
    private int dashCharges = MAX_DASH_CHARGES;
    private int dashRechargeTimer = 0;
    private boolean isDashing = false;
    private int dashDirection = 0;  // +1 前, -1 后
    private int dashTimer = 0;
    private static final int DASH_DURATION = 8; // 8帧

    // --- 特殊技资源 ---
    private int specialCooldownRemaining = 0;              // 影: CD剩余帧数
    private static final int SPECIAL_CD_FRAMES = 180;      // 3秒
    private int damageDealtSinceLastSpecial = 0;           // 武: 累计造成伤害
    private static final int DAMAGE_DEALT_THRESHOLD = 40;
    private int damageTakenSinceLastSpecial = 0;           // 刚: 累计受到伤害
    private static final int DAMAGE_TAKEN_THRESHOLD = 50;

    // --- 帧数据表: [前摇, 判定, 后摇] ---
    private static final int[] PUNCH_FRAMES = {3, 3, 5};
    private static final int[] KICK_FRAMES = {5, 3, 7};
    private static final int[] THROW_FRAMES = {4, 2, 6};
    private static final int[] KAGE_SPECIAL_FRAMES = {2, 2, 4};
    private static final int[] TAKESHI_SPECIAL_FRAMES = {4, 3, 5};
    private static final int[] GOU_SPECIAL_FRAMES = {6, 4, 6};

    // ========== 构造函数 ==========

    public Fighter(int id, float x, float y, FighterPreset preset, boolean facingRight) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.preset = preset;
        this.health = preset.getMaxHealth();
        this.facingRight = facingRight;
        this.velocityX = 0;
        this.velocityY = 0;
    }

    /** 向后兼容 — 默认 TAKESHI，朝右 */
    public Fighter(int id, float x, float y) {
        this(id, x, y, FighterPreset.TAKESHI, true);
    }

    // ========== 主更新 ==========

    public void update(InputCommand cmd, GameWorld world) {
        // 重置临时状态
        isAttacking = false;
        isHitFlag = false;

        // 自动面朝对手（除冲刺外）
        if (!isDashing) {
            Fighter opponent = world.getOpponentOf(this);
            if (opponent != null) {
                facingRight = opponent.getX() > this.x;
            }
        }

        // 固有时钟：冲刺冷却回复
        if (dashCharges < MAX_DASH_CHARGES) {
            dashRechargeTimer++;
            if (dashRechargeTimer >= DASH_RECHARGE_FRAMES) {
                dashCharges++;
                dashRechargeTimer = 0;
            }
        }

        // 影的CD冷却
        if (preset == FighterPreset.KAGE && specialCooldownRemaining > 0) {
            specialCooldownRemaining--;
        }

        // 冲刺中
        if (isDashing) {
            updateDash();
            return;
        }

        // 僵直中
        if (actionState == ActionState.STUN) {
            updateStun();
            return;
        }

        // 动作中（前摇/判定/后摇）
        if (actionState == ActionState.STARTUP || actionState == ActionState.ACTIVE
                || actionState == ActionState.RECOVERY) {
            updateAction();
            if (cmd != null && cmd.down) isBlocking = true;
            return;
        }

        // --- IDLE 状态，处理新输入 ---
        if (cmd == null || cmd.isEmpty()) {
            velocityX = 0;
            isBlocking = false;
            return;
        }

        // 冲刺和移动方向以角色面朝为准（非世界绝对方向）
        float forwardDir = facingRight ? 1f : -1f;

        if (cmd.dashForward && tryDash((int) forwardDir)) {
            return;
        }
        if (cmd.dashBackward && tryDash(-(int) forwardDir)) {
            return;
        }

        // 防御
        if (cmd.down) {
            isBlocking = true;
            velocityX = 0;
            return;
        }
        isBlocking = false;

        // 水平移动：left=后退(面朝反方向), right=前进(面朝方向)
        if (cmd.left && !cmd.right) {
            velocityX = -forwardDir * preset.getBackwardSpeed();
        } else if (cmd.right && !cmd.left) {
            velocityX = forwardDir * preset.getForwardSpeed();
        } else {
            velocityX = 0;
        }

        // 攻击动作
        if (cmd.punch) {
            startAction(ActionType.PUNCH);
        } else if (cmd.kick) {
            startAction(ActionType.KICK);
        } else if (cmd.up) {
            startAction(ActionType.THROW);
        } else if (cmd.special && isSpecialReady()) {
            startAction(ActionType.SPECIAL);
            audioSpecialTrigger = true;
            onSpecialUsed();
        }

        applyMovement();
    }

    // ========== 动作状态机 ==========

    private void startAction(ActionType type) {
        this.actionType = type;
        int[] frames = getFrameData(type);
        this.actionState = ActionState.STARTUP;
        this.actionTimer = frames[0];
        this.velocityX = 0;

        // 冲刺中出招：前摇减半
        if (isDashing) {
            this.actionTimer = Math.max(1, this.actionTimer / 2);
        }
    }

    private void updateAction() {
        actionTimer--;
        if (actionTimer <= 0) {
            int[] frames = getFrameData(actionType);
            if (actionState == ActionState.STARTUP) {
                actionState = ActionState.ACTIVE;
                actionTimer = frames[1];
                isAttacking = true;
            } else if (actionState == ActionState.ACTIVE) {
                // 影的特殊技可以取消后摇
                if (preset == FighterPreset.KAGE && actionType == ActionType.SPECIAL) {
                    actionState = ActionState.IDLE;
                    actionType = ActionType.NONE;
                    actionTimer = 0;
                    return;
                }
                actionState = ActionState.RECOVERY;
                actionTimer = frames[2];
            } else {
                actionState = ActionState.IDLE;
                actionType = ActionType.NONE;
                actionTimer = 0;
            }
        }
    }

    private void updateStun() {
        stunRemaining--;
        if (stunRemaining <= 0) {
            actionState = ActionState.IDLE;
            stunRemaining = 0;
        }
        velocityX = 0;
    }

    private void updateDash() {
        dashTimer--;
        float dist = dashDirection > 0
                ? preset.getDashForwardDist() / (float) DASH_DURATION
                : preset.getDashBackwardDist() / (float) DASH_DURATION;
        x += dashDirection * dist;
        if (dashTimer <= 0) {
            isDashing = false;
            dashTimer = 0;
            actionState = ActionState.IDLE;
            velocityX = 0;
        }
    }

    private int[] getFrameData(ActionType type) {
        switch (type) {
            case PUNCH: return PUNCH_FRAMES;
            case KICK: return KICK_FRAMES;
            case THROW: return THROW_FRAMES;
            case SPECIAL:
                switch (preset) {
                    case KAGE: return KAGE_SPECIAL_FRAMES;
                    case TAKESHI: return TAKESHI_SPECIAL_FRAMES;
                    case GOU: return GOU_SPECIAL_FRAMES;
                    default: return TAKESHI_SPECIAL_FRAMES;
                }
            default: return PUNCH_FRAMES;
        }
    }

    private static final float WORLD_MIN_X = -300f;
    private static final float WORLD_MAX_X = 1300f;

    private void applyMovement() {
        x += velocityX;
        y += velocityY;
        if (y > GROUND_Y) {
            y = GROUND_Y;
            velocityY = 0;
        }
        // 软边界：防止无限逃跑，平滑回推而非硬钳制
        if (x < WORLD_MIN_X) velocityX += 2f;
        else if (x > WORLD_MAX_X) velocityX -= 2f;
    }

    // ========== 冲刺系统 ==========

    private boolean tryDash(int direction) {
        if (dashCharges <= 0 || isDashing || actionState == ActionState.STUN) return false;
        if (actionState != ActionState.IDLE) return false;

        dashCharges--;
        dashRechargeTimer = 0;
        isDashing = true;
        dashDirection = direction;
        dashTimer = DASH_DURATION;
        actionState = ActionState.DASH;
        audioDashTrigger = true;
        facingRight = (direction > 0);
        return true;
    }

    public boolean isDashAttacking() {
        return isDashing && actionState != ActionState.IDLE && actionState != ActionState.DASH;
    }

    // ========== 特殊技资源 ==========

    public boolean isSpecialReady() {
        switch (preset) {
            case KAGE: return specialCooldownRemaining <= 0;
            case TAKESHI: return damageDealtSinceLastSpecial >= DAMAGE_DEALT_THRESHOLD;
            case GOU: return damageTakenSinceLastSpecial >= DAMAGE_TAKEN_THRESHOLD;
            default: return false;
        }
    }

    private void onSpecialUsed() {
        switch (preset) {
            case KAGE:
                specialCooldownRemaining = SPECIAL_CD_FRAMES;
                break;
            case TAKESHI:
                damageDealtSinceLastSpecial = 0;
                break;
            case GOU:
                damageTakenSinceLastSpecial = 0;
                break;
        }
    }

    public void onDamageDealt(int dmg) {
        if (preset == FighterPreset.TAKESHI) {
            damageDealtSinceLastSpecial += dmg;
        }
    }

    public void onDamageTaken(int dmg) {
        if (preset == FighterPreset.GOU) {
            damageTakenSinceLastSpecial += dmg;
        }
    }

    // ========== 受伤与僵直 ==========

    public void takeDamage(int damage, boolean isThrow) {
        this.lastRawDamageReceived = damage; // 记录原始伤害（减免前），供 hit-stop 判断
        int actualDamage = damage;
        if (!isThrow && isBlocking) {
            actualDamage = damage / 2;
        }
        this.health -= actualDamage;
        if (this.health < 0) this.health = 0;
        this.isHitFlag = true;

        int stunFrames = 5 + (actualDamage / 5);
        if (isThrow && isBlocking) {
            stunFrames += 2;
        }
        if (stunFrames > 20) stunFrames = 20;

        this.actionState = ActionState.STUN;
        this.stunRemaining = stunFrames;
        this.actionType = ActionType.NONE;
        this.actionTimer = 0;
        this.isBlocking = false;
        this.velocityX = 0;

        // GOU 资源累加统一由 onDamageTaken() 处理，避免重复计数
    }

    /** 向后兼容 — takeDamage(int) 默认为非投技 */
    public void takeDamage(int damage) {
        takeDamage(damage, false);
    }

    /** 短僵直（相杀用），固定3帧 */
    public void applyShortStun() {
        this.actionState = ActionState.STUN;
        this.stunRemaining = 3;
        this.actionType = ActionType.NONE;
        this.actionTimer = 0;
        this.isBlocking = false;
        this.velocityX = 0;
    }

    // ========== 判定框 ==========

    public Rect getHitbox() {
        int w = preset.getHitboxWidth();
        int h = preset.getHitboxHeight();
        return new Rect((int) x - w / 2, (int) GROUND_Y - h, w, h);
    }

    public Rect getAttackBox() {
        if (actionState != ActionState.ACTIVE) {
            return new Rect(0, 0, 0, 0);
        }

        int facingSign = facingRight ? 1 : -1;
        int boxX, boxW, boxH;

        switch (actionType) {
            case PUNCH:
                boxW = 50; boxH = 30;
                boxX = (int) x + facingSign * (preset.getHitboxWidth() / 2);
                break;
            case KICK:
                boxW = 35; boxH = 25;
                boxX = (int) x + facingSign * (preset.getHitboxWidth() / 2 + 15);
                break;
            case THROW:
                boxW = 30; boxH = 20;
                boxX = (int) x + facingSign * (preset.getHitboxWidth() / 2);
                break;
            case SPECIAL:
                switch (preset) {
                    case KAGE:
                        boxW = 60; boxH = 30;
                        boxX = (int) x + facingSign * (preset.getHitboxWidth() / 2);
                        break;
                    case TAKESHI:
                        boxW = 40; boxH = 35;
                        boxX = (int) x + facingSign * (preset.getHitboxWidth() / 2);
                        break;
                    case GOU:
                        boxW = 80; boxH = 25;
                        boxX = (int) x - 40;
                        break;
                    default:
                        boxW = 0; boxH = 0; boxX = 0;
                }
                break;
            default:
                boxW = 0; boxH = 0; boxX = 0;
        }

        if (!facingRight && actionType != ActionType.SPECIAL) boxX -= boxW;
        return new Rect(boxX, (int) GROUND_Y - boxH, boxW, boxH);
    }

    // ========== 姿态 ==========

    public FighterStance getStance() {
        if (actionState == ActionState.STUN) return FighterStance.HURT;
        if (isDashing) return FighterStance.DASH;
        if (isBlocking && actionState == ActionState.IDLE) return FighterStance.BLOCK;
        if (actionState == ActionState.IDLE) {
            if (velocityX > 0.1f) return FighterStance.WALK_FORWARD;
            if (velocityX < -0.1f) return FighterStance.WALK_BACKWARD;
            return FighterStance.IDLE;
        }
        switch (actionType) {
            case PUNCH: return FighterStance.PUNCH;
            case KICK: return FighterStance.KICK;
            case THROW: return FighterStance.THROW;
            case SPECIAL: return FighterStance.SPECIAL;
            default: return FighterStance.IDLE;
        }
    }

    // ========== Getters ==========

    public int getId() { return id; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getVelocityX() { return velocityX; }
    public float getVelocityY() { return velocityY; }
    public int getHealth() { return health; }
    public int getMaxHealth() { return preset.getMaxHealth(); }
    public FighterPreset getPreset() { return preset; }
    public boolean isFacingRight() { return facingRight; }
    public boolean isAttacking() { return actionState == ActionState.ACTIVE; }
    public boolean isHit() { return isHitFlag; }
    public boolean isBlocking() { return isBlocking; }
    public boolean isInStun() { return actionState == ActionState.STUN; }

    // --- 音效触发标志 ---
    public boolean consumeAudioDashTrigger() {
        boolean v = audioDashTrigger; audioDashTrigger = false; return v;
    }
    public boolean consumeAudioSpecialTrigger() {
        boolean v = audioSpecialTrigger; audioSpecialTrigger = false; return v;
    }
    public boolean consumeAudioBlockedTrigger() {
        boolean v = audioBlockedTrigger; audioBlockedTrigger = false; return v;
    }
    public void setAudioBlockedTrigger() { this.audioBlockedTrigger = true; }
    public int getStunRemaining() { return stunRemaining; }
    public int getLastRawDamageReceived() { return lastRawDamageReceived; }
    public ActionState getActionState() { return actionState; }
    public ActionType getActionType() { return actionType; }
    public int getDashCharges() { return dashCharges; }
    public boolean isDashing() { return isDashing; }

    public int getSpecialCooldownRemaining() { return specialCooldownRemaining; }
    public int getDamageDealtSinceLastSpecial() { return damageDealtSinceLastSpecial; }
    public int getDamageTakenSinceLastSpecial() { return damageTakenSinceLastSpecial; }
    public int getDashDirection() { return dashDirection; }
    public int getDashTimer() { return dashTimer; }
    public int getDashRechargeTimer() { return dashRechargeTimer; }
    public int getActionTimer() { return actionTimer; }

    // ========== Setters ==========

    public void setPosition(float x, float y) { this.x = x; this.y = y; }
    public void setVelocity(float vx, float vy) { this.velocityX = vx; this.velocityY = vy; }
    public void setHealth(int health) { this.health = Math.max(0, health); }
    public void setFacingRight(boolean fr) { this.facingRight = fr; }
    public void setActionState(ActionState s) { this.actionState = s; }
    public void setActionType(ActionType t) { this.actionType = t; }
    public void setActionTimer(int t) { this.actionTimer = t; }
    public void setBlocking(boolean b) { this.isBlocking = b; }
    public void setStunRemaining(int r) { this.stunRemaining = r; }
    public void setDashCharges(int c) { this.dashCharges = c; }
    public void setDashRechargeTimer(int t) { this.dashRechargeTimer = t; }
    public void setDashing(boolean d) { this.isDashing = d; }
    public void setDashDirection(int d) { this.dashDirection = d; }
    public void setDashTimer(int t) { this.dashTimer = t; }
    public void setSpecialCooldownRemaining(int r) { this.specialCooldownRemaining = r; }
    public void setDamageDealtSinceLastSpecial(int d) { this.damageDealtSinceLastSpecial = d; }
    public void setDamageTakenSinceLastSpecial(int d) { this.damageTakenSinceLastSpecial = d; }
}
