package com.Fyren.game;

import java.awt.Color;

/**
 * 角色预设 — 定义每个角色的属性、判定框尺寸、视觉参数。
 * 三个可用角色: KAGE (影, 快速轻量), TAKESHI (武, 均衡), GOU (刚, 重型慢速)
 */
public enum FighterPreset {
    KAGE("影", 80, 15, 45, 5.5f, 2.2f, 40, 90, 120, 70, 2, new Color(0x2255CC)),
    TAKESHI("武", 100, 10, 50, 4.0f, 1.8f, 50, 100, 90, 50, 3, new Color(0xCC3333)),
    GOU("刚", 130, 12, 60, 2.8f, 1.2f, 60, 110, 70, 40, 5, new Color(0x2D6A4F));

    private final String displayName;
    private final int maxHealth;
    private final int baseDamage;
    private final int attackRange;      // 攻击距离(px)
    private final float forwardSpeed;   // 前进速度(px/frame)
    private final float backwardSpeed;  // 后退速度(px/frame)
    private final int hitboxWidth;      // 受击框宽度
    private final int hitboxHeight;     // 受击框高度
    private final int dashForwardDist;  // 前冲刺距离
    private final int dashBackwardDist; // 后冲刺距离
    private final int lineWidth;        // 火柴人线条宽度
    private final Color lineColor;      // 火柴人线条颜色

    FighterPreset(String displayName, int maxHealth, int baseDamage, int attackRange,
                  float forwardSpeed, float backwardSpeed, int hitboxWidth, int hitboxHeight,
                  int dashForwardDist, int dashBackwardDist,
                  int lineWidth, Color lineColor) {
        this.displayName = displayName;
        this.maxHealth = maxHealth;
        this.baseDamage = baseDamage;
        this.attackRange = attackRange;
        this.forwardSpeed = forwardSpeed;
        this.backwardSpeed = backwardSpeed;
        this.hitboxWidth = hitboxWidth;
        this.hitboxHeight = hitboxHeight;
        this.dashForwardDist = dashForwardDist;
        this.dashBackwardDist = dashBackwardDist;
        this.lineWidth = lineWidth;
        this.lineColor = lineColor;
    }

    public String getDisplayName() { return displayName; }
    public int getMaxHealth() { return maxHealth; }
    public int getBaseDamage() { return baseDamage; }
    public int getAttackRange() { return attackRange; }
    public float getForwardSpeed() { return forwardSpeed; }
    public float getBackwardSpeed() { return backwardSpeed; }
    public int getHitboxWidth() { return hitboxWidth; }
    public int getHitboxHeight() { return hitboxHeight; }
    public int getDashForwardDist() { return dashForwardDist; }
    public int getDashBackwardDist() { return dashBackwardDist; }
    public int getLineWidth() { return lineWidth; }
    public Color getLineColor() { return lineColor; }
}
