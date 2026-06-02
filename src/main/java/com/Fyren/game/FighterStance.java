package com.Fyren.game;

/**
 * 火柴人姿态 — 驱动 StickFigureRenderer 的绘制选择。
 * 由 Fighter.getStance() 根据内部状态机计算，渲染层只读取。
 */
public enum FighterStance {
    IDLE,
    WALK_FORWARD,
    WALK_BACKWARD,
    PUNCH,
    KICK,
    THROW,
    SPECIAL,
    BLOCK,
    HURT,
    DASH
}
