package com.Fyren.match;

import java.util.*;
import java.util.concurrent.*;

/**
 * 匹配器 — 基于隐藏分（MMR）的玩家匹配队列
 *
 * 匹配策略：
 * 1. 玩家加入匹配队列，携带当前MMR
 * 2. 系统按MMR排序，优先匹配分数接近的玩家
 * 3. 采用"扩散窗口"策略：等待时间越长，可匹配的MMR范围越大
 * 4. 当两个玩家的MMR差值在允许范围内时，配对成功
 *
 * 扩散窗口公式：
 *   maxDiff = baseDiff + waitTime * expandRate
 *   baseDiff = 50（初始50分内匹配）
 *   expandRate = 5（每秒扩大5分范围）
 *   maxDiffCap = 400（最大400分差）
 */
public class Matchmaker {
    // 匹配参数
    private static final int BASE_MMR_DIFF = 50;       // 基础MMR差值
    private static final int MMR_DIFF_EXPAND_RATE = 5; // 每秒扩展范围
    private static final int MAX_MMR_DIFF = 400;       // 最大MMR差值上限
    private static final long MATCH_CHECK_INTERVAL_MS = 1000; // 匹配检查间隔

    // 匹配队列
    private final PriorityQueue<MatchEntry> matchQueue = new PriorityQueue<>(
            Comparator.comparingLong(e -> e.enqueueTime)
    );

    // 匹配历史（防止短时间内重复匹配）
    private final Map<Integer, Set<Integer>> recentOpponents = new ConcurrentHashMap<>();
    private static final int RECENT_OPPONENT_LIMIT = 10; // 记住最近10个对手
    private static final long AVOID_REMATCH_MS = 60_000; // 60秒内避免重复匹配

    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    // 匹配成功回调
    private MatchCallback onMatchFound;

    @FunctionalInterface
    public interface MatchCallback {
        void onMatch(MatchEntry player1, MatchEntry player2);
    }

    /**
     * 匹配队列条目
     */
    public static class MatchEntry {
        public final int playerId;
        public final int rating;
        public final long enqueueTime;

        public MatchEntry(int playerId, int rating) {
            this.playerId = playerId;
            this.rating = rating;
            this.enqueueTime = System.currentTimeMillis();
        }

        /**
         * 计算当前允许的MMR差值范围
         */
        public int getAllowedDiff() {
            long waitMs = System.currentTimeMillis() - enqueueTime;
            int diff = BASE_MMR_DIFF + (int) (waitMs / 1000) * MMR_DIFF_EXPAND_RATE;
            return Math.min(diff, MAX_MMR_DIFF);
        }

        /**
         * 获取等待时间（秒）
         */
        public double getWaitSeconds() {
            return (System.currentTimeMillis() - enqueueTime) / 1000.0;
        }

        @Override
        public String toString() {
            return String.format("MatchEntry[player=%d, rating=%d, wait=%.1fs, allowedDiff=%d]",
                    playerId, rating, getWaitSeconds(), getAllowedDiff());
        }
    }

    /**
     * 启动匹配器
     */
    public void start() {
        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::processMatches, 0, MATCH_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        System.out.println("[Matchmaker] 匹配器已启动");
    }

    /**
     * 停止匹配器
     */
    public void stop() {
        running = false;
        if (scheduler != null) scheduler.shutdown();
        matchQueue.clear();
        System.out.println("[Matchmaker] 匹配器已停止");
    }

    /**
     * 玩家加入匹配队列
     */
    public synchronized void enqueue(int playerId, int rating) {
        // 检查是否已在队列中
        Optional<MatchEntry> existing = matchQueue.stream()
                .filter(e -> e.playerId == playerId)
                .findFirst();

        if (existing.isPresent()) {
            // 已在队列中，用新rating更新（但保留原等待时间）
            System.out.println("[Matchmaker] 玩家" + playerId + "已在队列中，更新rating: "
                    + existing.get().rating + " → " + rating);
            // 移除旧的，加入新的（保留加入时间逻辑上用新的entry替换）
            matchQueue.remove(existing.get());
        }

        MatchEntry entry = new MatchEntry(playerId, rating);
        matchQueue.offer(entry);
        System.out.println("[Matchmaker] 玩家" + playerId + "加入匹配队列 (rating=" + rating + ")");
    }

    /**
     * 玩家取消匹配
     */
    public synchronized void dequeue(int playerId) {
        matchQueue.removeIf(e -> e.playerId == playerId);
        System.out.println("[Matchmaker] 玩家" + playerId + "离开匹配队列");
    }

    /**
     * 处理匹配逻辑（定期执行）
     */
    private synchronized void processMatches() {
        if (matchQueue.size() < 2) return;

        List<MatchEntry> entries = new ArrayList<>(matchQueue);
        // 按rating排序以便高效匹配
        entries.sort(Comparator.comparingInt(e -> e.rating));

        boolean matchFound = false;
        for (int i = 0; i < entries.size() - 1 && !matchFound; i++) {
            MatchEntry a = entries.get(i);
            for (int j = i + 1; j < entries.size(); j++) {
                MatchEntry b = entries.get(j);

                int ratingDiff = Math.abs(a.rating - b.rating);
                int allowedDiff = Math.max(a.getAllowedDiff(), b.getAllowedDiff());

                if (ratingDiff <= allowedDiff && !isRecentOpponent(a.playerId, b.playerId)) {
                    // 匹配成功！
                    System.out.printf("[Matchmaker] 匹配成功! 玩家%d(%d分) vs 玩家%d(%d分), "
                                    + "分差=%d, 等待=%.1fs/%.1fs\n",
                            a.playerId, a.rating, b.playerId, b.rating,
                            ratingDiff, a.getWaitSeconds(), b.getWaitSeconds());

                    // 从队列移除
                    matchQueue.remove(a);
                    matchQueue.remove(b);

                    // 记录为最近对手
                    addRecentOpponent(a.playerId, b.playerId);

                    // 回调通知
                    if (onMatchFound != null) {
                        onMatchFound.onMatch(a, b);
                    }

                    matchFound = true;
                    break;
                }
            }
        }
    }

    /**
     * 检查是否是最近对手（避免短时间内重复匹配）
     */
    private boolean isRecentOpponent(int p1, int p2) {
        Set<Integer> opponents = recentOpponents.get(p1);
        return opponents != null && opponents.contains(p2);
    }

    /**
     * 记录为最近对手
     */
    private void addRecentOpponent(int p1, int p2) {
        recentOpponents.computeIfAbsent(p1, k -> new HashSet<>()).add(p2);
        recentOpponents.computeIfAbsent(p2, k -> new HashSet<>()).add(p1);

        // 延迟清理（60秒后可重新匹配）
        scheduler.schedule(() -> {
            Set<Integer> s1 = recentOpponents.get(p1);
            if (s1 != null) s1.remove(p2);
            Set<Integer> s2 = recentOpponents.get(p2);
            if (s2 != null) s2.remove(p1);
        }, AVOID_REMATCH_MS, TimeUnit.MILLISECONDS);
    }

    // ========== Getters / Setters ==========

    public void setOnMatchFound(MatchCallback callback) {
        this.onMatchFound = callback;
    }

    public int getQueueSize() {
        return matchQueue.size();
    }

    public List<MatchEntry> getQueueSnapshot() {
        return new ArrayList<>(matchQueue);
    }
}
