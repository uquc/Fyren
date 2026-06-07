package com.Fyren.game;

// Uses com.Fyren.game.Rect (GWT-compatible, same package)

/**
 * 碰撞系统 — 基于帧数据的判定框碰撞检测。
 *
 * 规则:
 * - 攻击框 ∩ 受击框 ≠ ∅ 且攻击方在判定帧 → 命中
 * - 双方同时判定帧且攻击框相交 → 相杀（各受半伤+短僵直）
 * - 投技破防（防御状态下投技命中 → 全额伤害+额外僵直）
 * - 冲刺攻击：伤害-5
 */
public class CollisionSystem {

    public void checkCollisions(Fighter p1, Fighter p2) {
        Rect a1 = p1.getAttackBox();
        Rect h2 = p2.getHitbox();
        Rect a2 = p2.getAttackBox();
        Rect h1 = p1.getHitbox();

        boolean p1Attacking = p1.isAttacking() && a1.width > 0 && a1.height > 0;
        boolean p2Attacking = p2.isAttacking() && a2.width > 0 && a2.height > 0;

        boolean p1HitsP2 = p1Attacking && a1.intersects(h2);
        boolean p2HitsP1 = p2Attacking && a2.intersects(h1);

        // 相杀：双方同时命中
        if (p1HitsP2 && p2HitsP1) {
            handleClash(p1, p2);
            return;
        }

        if (p1HitsP2) {
            applyHit(p1, p2);
        }
        if (p2HitsP1) {
            applyHit(p2, p1);
        }

        resolveOverlap(p1, p2);
    }

    /** 计算攻击者的实际基础伤害（含招式类型修正和冲刺修正） */
    private int calculateDamage(Fighter attacker) {
        int dmg = attacker.getPreset().getBaseDamage();

        switch (attacker.getActionType()) {
            case KICK:  dmg += 2; break;
            case THROW: dmg -= 3; break;
            case SPECIAL:
                switch (attacker.getPreset()) {
                    case KAGE: dmg = 12; break;
                    case TAKESHI: dmg = 14; break;
                    case GOU: dmg = 18; break;
                }
                break;
            default: break;
        }

        if (attacker.isDashAttacking()) {
            dmg = Math.max(1, dmg - 5);
        }
        return dmg;
    }

    private void applyHit(Fighter attacker, Fighter defender) {
        int baseDmg = calculateDamage(attacker);

        boolean isThrow = (attacker.getActionType() == Fighter.ActionType.THROW);
        // 提前计算实际伤害，避免 takeDamage() 清除 isBlocking 后误判
        int effectiveDmg = defender.isBlocking() && !isThrow ? baseDmg / 2 : baseDmg;
        defender.takeDamage(baseDmg, isThrow);

        attacker.onDamageDealt(baseDmg);
        defender.onDamageTaken(effectiveDmg);

        // 武·气合掌 击退
        if (attacker.getActionType() == Fighter.ActionType.SPECIAL
                && attacker.getPreset() == FighterPreset.TAKESHI) {
            float pushDir = attacker.isFacingRight() ? 1f : -1f;
            defender.setPosition(defender.getX() + pushDir * 50, defender.getY());
        }

        // 影·影袭 突进
        if (attacker.getActionType() == Fighter.ActionType.SPECIAL
                && attacker.getPreset() == FighterPreset.KAGE) {
            float dashDir = attacker.isFacingRight() ? 1f : -1f;
            attacker.setPosition(attacker.getX() + dashDir * 60, attacker.getY());
        }
    }

    private void handleClash(Fighter p1, Fighter p2) {
        int dmg1 = calculateDamage(p1) / 2;
        int dmg2 = calculateDamage(p2) / 2;

        p1.setHealth(p1.getHealth() - dmg2);
        p2.setHealth(p2.getHealth() - dmg1);
        if (p1.getHealth() < 0) p1.setHealth(0);
        if (p2.getHealth() < 0) p2.setHealth(0);

        p1.lastRawDamageReceived = dmg2; // 相杀无防御，实际伤害=原始伤害
        p2.lastRawDamageReceived = dmg1;
        p1.applyShortStun();
        p2.applyShortStun();

        p1.onDamageDealt(dmg1);
        p1.onDamageTaken(dmg2);
        p2.onDamageDealt(dmg2);
        p2.onDamageTaken(dmg1);
    }

    private void resolveOverlap(Fighter p1, Fighter p2) {
        Rect h1 = p1.getHitbox();
        Rect h2 = p2.getHitbox();
        if (!h1.intersects(h2)) return;

        int overlap = (int) (Math.min(h1.getMaxX(), h2.getMaxX())
                - Math.max(h1.getMinX(), h2.getMinX()));
        if (overlap <= 0) return;

        float push = overlap / 2.0f;
        if (p1.getX() < p2.getX()) {
            p1.setPosition(p1.getX() - push, p1.getY());
            p2.setPosition(p2.getX() + push, p2.getY());
        } else {
            p1.setPosition(p1.getX() + push, p1.getY());
            p2.setPosition(p2.getX() - push, p2.getY());
        }
    }
}
