package com.Fyren.game;

/**
 * 轻量 AABB 矩形 — 替代 java.awt.Rectangle（GWT 兼容）。
 * 仅包含碰撞检测所需方法。
 */
public class Rect {
    public final int x, y, width, height;

    public Rect(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean intersects(Rect other) {
        return this.x < other.x + other.width
            && this.x + this.width > other.x
            && this.y < other.y + other.height
            && this.y + this.height > other.y;
    }

    public int getMinX() { return x; }
    public int getMaxX() { return x + width; }
    public int getMinY() { return y; }
    public int getMaxY() { return y + height; }
}
