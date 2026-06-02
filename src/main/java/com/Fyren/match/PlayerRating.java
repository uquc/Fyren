package com.Fyren.match;

/**
 * 玩家隐藏分（MMR） — 基于ELO算法的评分系统
 *
 * ELO算法核心：
 * - 每个玩家有一个评分（默认1500）
 * - 胜率期望由双方评分差决定
 * - 实际结果与期望的偏差决定评分变化量
 * - K因子控制评分波动幅度
 *
 * 隐藏分的意义：
 * - 玩家看不到自己的MMR，只能看到段位
 * - 匹配系统使用MMR而非段位进行匹配
 * - 避免玩家通过操控段位来获取不公平匹配
 */
public class PlayerRating {
    // ELO常量
    public static final int DEFAULT_RATING = 1500;
    public static final double BASE_K_FACTOR = 32.0;
    public static final double SCALE_FACTOR = 400.0;

    // 段位定义（基于MMR区间）
    public enum Rank {
        BRONZE(0, 1200, "青铜"),
        SILVER(1200, 1400, "白银"),
        GOLD(1400, 1600, "黄金"),
        PLATINUM(1600, 1800, "白金"),
        DIAMOND(1800, 2000, "钻石"),
        MASTER(2000, 2200, "大师"),
        GRANDMASTER(2200, Integer.MAX_VALUE, "宗师");

        public final int minRating;
        public final int maxRating;
        public final String displayName;

        Rank(int minRating, int maxRating, String displayName) {
            this.minRating = minRating;
            this.maxRating = maxRating;
            this.displayName = displayName;
        }

        /**
         * 根据MMR获取对应段位
         */
        public static Rank fromRating(int rating) {
            for (Rank rank : values()) {
                if (rating >= rank.minRating && rating < rank.maxRating) {
                    return rank;
                }
            }
            return BRONZE;
        }
    }

    private final int playerId;
    private int rating;           // 当前MMR
    private int peakRating;       // 历史最高MMR
    private int wins;             // 胜场数
    private int losses;           // 负场数
    private int draws;            // 平局数
    private int winStreak;        // 连胜次数
    private int lossStreak;       // 连败次数
    private int totalGames;       // 总对局数

    public PlayerRating(int playerId) {
        this(playerId, DEFAULT_RATING);
    }

    public PlayerRating(int playerId, int initialRating) {
        this.playerId = playerId;
        this.rating = initialRating;
        this.peakRating = initialRating;
    }

    /**
     * 计算对A的期望胜率（A对B）
     * E_A = 1 / (1 + 10^((R_B - R_A) / 400))
     */
    public double expectedScore(PlayerRating opponent) {
        return 1.0 / (1.0 + Math.pow(10.0, (opponent.rating - this.rating) / SCALE_FACTOR));
    }

    /**
     * 根据比赛结果更新MMR
     *
     * @param opponent       对手的评分
     * @param actualScore    实际得分：1.0=胜, 0.5=平, 0.0=负
     */
    public void updateRating(PlayerRating opponent, double actualScore) {
        double expected = expectedScore(opponent);

        // K因子动态调整：新手K值更高，老手更稳定
        double kFactor = getKFactor();

        // ELO公式：R' = R + K * (S - E)
        int ratingChange = (int) Math.round(kFactor * (actualScore - expected));

        // 应用评分变化
        int oldRating = this.rating;
        this.rating += ratingChange;

        // 更新历史最高
        if (this.rating > this.peakRating) {
            this.peakRating = this.rating;
        }

        // 更新统计
        this.totalGames++;
        if (actualScore >= 1.0) {
            this.wins++;
            this.winStreak++;
            this.lossStreak = 0;
        } else if (actualScore <= 0.0) {
            this.losses++;
            this.lossStreak++;
            this.winStreak = 0;
        } else {
            this.draws++;
            this.winStreak = 0;
            this.lossStreak = 0;
        }

        System.out.printf("[MMR] 玩家%d: %d → %d (%+d) | 对手%d: %d | K=%.1f | 胜率期望=%.2f%%\n",
                playerId, oldRating, rating, ratingChange,
                opponent.playerId, opponent.rating, kFactor, expected * 100);
    }

    /**
     * 动态K因子
     * - 前30局：K=64（快速定位）
     * - 30-100局：K=32（稳定期）
     * - 100局以上：K=16（微调期）
     * - 连胜/连败加成：每连胜2局+2 K值（上限+16）
     */
    private double getKFactor() {
        double k = BASE_K_FACTOR;

        if (totalGames < 30) {
            k = 64.0;
        } else if (totalGames < 100) {
            k = 32.0;
        } else {
            k = 16.0;
        }

        // 连胜/连败时略微增加K值以加速收敛
        int streak = Math.max(winStreak, lossStreak);
        if (streak > 2) {
            k += Math.min(streak * 2, 16);
        }

        return k;
    }

    /**
     * 获取当前段位
     */
    public Rank getRank() {
        return Rank.fromRating(rating);
    }

    /**
     * 获取胜率（百分比）
     */
    public double getWinRate() {
        if (totalGames == 0) return 0;
        return (double) wins / totalGames * 100.0;
    }

    /**
     * 重置评分（用于赛季重置等场景）
     */
    public void reset() {
        this.rating = DEFAULT_RATING;
        this.peakRating = DEFAULT_RATING;
    }

    /**
     * 软重置（向默认分回归）
     */
    public void softReset() {
        this.rating = (this.rating + DEFAULT_RATING) / 2;
    }

    // ========== Getters ==========

    public int getPlayerId() { return playerId; }
    public int getRating() { return rating; }
    public int getPeakRating() { return peakRating; }
    public int getWins() { return wins; }
    public int getLosses() { return losses; }
    public int getDraws() { return draws; }
    public int getWinStreak() { return winStreak; }
    public int getLossStreak() { return lossStreak; }
    public int getTotalGames() { return totalGames; }

    @Override
    public String toString() {
        return String.format("PlayerRating[id=%d, rating=%d, rank=%s, W/L/D=%d/%d/%d, winRate=%.1f%%]",
                playerId, rating, getRank().displayName, wins, losses, draws, getWinRate());
    }
}
