// FrameSyncManager.java
package com.Fyren.sync;

import com.Fyren.game.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 帧同步管理器 - 格斗游戏的核心
 * 采用乐观帧锁定 + 回滚机制
 */
public class FrameSyncManager {
    private static final int TARGET_FPS = 60;
    private static final int FRAME_TIME_MS = 1000 / TARGET_FPS;
    private static final int ROLLBACK_MAX_FRAMES = 10; // 最大回滚帧数

    /**
     * 本地输入提供者 — 由GameClient注入，解耦输入源
     */
    @FunctionalInterface
    public interface LocalInputProvider {
        InputCommand getInput(int frameNumber, int localPlayerId);
    }

    private final GameWorld gameWorld;
    private final InputBuffer localInputBuffer;
    private final Map<Integer, InputBuffer> remoteInputBuffers = new ConcurrentHashMap<>();

    private volatile boolean running = false;
    private Thread gameLoopThread;

    private LocalInputProvider localInputProvider;
    private int localPlayerId = 1;

    // 预测相关
    private final Map<Integer, InputCommand> lastKnownInputs = new ConcurrentHashMap<>();
    private int confirmedFrame = 0; // 已确认的最高帧

    public FrameSyncManager(GameWorld gameWorld) {
        this.gameWorld = gameWorld;
        this.localInputBuffer = new InputBuffer(120); // 2秒缓冲
    }

    public void setLocalInputProvider(LocalInputProvider provider) {
        this.localInputProvider = provider;
    }

    public void setLocalPlayerId(int localPlayerId) {
        this.localPlayerId = localPlayerId;
    }

    /**
     * 启动游戏循环
     */
    public void start() {
        running = true;
        gameLoopThread = new Thread(this::gameLoop);
        gameLoopThread.start();
    }

    public void stop() {
        running = false;
    }

    /**
     * 游戏主循环 - 固定时间步长
     */
    private void gameLoop() {
        long lastUpdateTime = System.currentTimeMillis();
        int currentFrame = 0;

        while (running) {
            long now = System.currentTimeMillis();
            long elapsed = now - lastUpdateTime;

            if (elapsed >= FRAME_TIME_MS) {
                // 1. 收集本地输入
                InputCommand localCmd = collectLocalInput(currentFrame);
                localInputBuffer.addInput(localCmd);

                // 2. 获取所有玩家的输入（本地 + 远程）
                List<InputCommand> allInputs = gatherInputs(currentFrame);

                // 3. 预测：如果缺少远程输入，使用上一次的输入预测
                List<InputCommand> inputsForSimulation = predictInputs(allInputs, currentFrame);

                // 4. 推进游戏逻辑
                gameWorld.update(inputsForSimulation, currentFrame);

                // 5. 检查是否需要回滚
                checkAndRollback(currentFrame);

                currentFrame++;
                lastUpdateTime = now;

                // 控制帧率
                long sleepTime = FRAME_TIME_MS - (System.currentTimeMillis() - now);
                if (sleepTime > 0) {
                    try { Thread.sleep(sleepTime); } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    /**
     * 收集本地玩家输入
     */
    private InputCommand collectLocalInput(int frameNumber) {
        if (localInputProvider != null) {
            return localInputProvider.getInput(frameNumber, localPlayerId);
        }
        return new InputCommand(frameNumber, localPlayerId);
    }

    /**
     * 收集所有玩家的输入
     */
    private List<InputCommand> gatherInputs(int frameNumber) {
        List<InputCommand> inputs = new ArrayList<>();

        // 本地输入
        inputs.add(localInputBuffer.getInput(frameNumber));

        // 远程输入
        for (InputBuffer remoteBuffer : remoteInputBuffers.values()) {
            inputs.add(remoteBuffer.getInput(frameNumber));
        }

        return inputs;
    }

    /**
     * 预测缺失的输入
     */
    private List<InputCommand> predictInputs(List<InputCommand> inputs, int frameNumber) {
        List<InputCommand> result = new ArrayList<>();

        for (InputCommand cmd : inputs) {
            if (cmd != null && !cmd.isEmpty()) {
                result.add(cmd);
                lastKnownInputs.put(cmd.playerId, cmd);
            } else if (cmd != null) {
                // cmd不为空但操作为空（空指令），直接使用
                result.add(cmd);
                // 不更新lastKnownInputs，保留上一帧的有效输入用于后续预测
            } else {
                // cmd为null，说明该玩家输入尚未到达，尝试预测
                // 从inputs列表顺序无法确定playerId，遍历remoteInputBuffers查找缺失的玩家
                for (Map.Entry<Integer, InputBuffer> entry : remoteInputBuffers.entrySet()) {
                    int remotePlayerId = entry.getKey();
                    InputCommand bufCmd = entry.getValue().getInput(frameNumber);
                    if (bufCmd.isEmpty() || bufCmd.playerId == -1) {
                        // 该远程玩家的输入缺失，使用预测
                        InputCommand predicted = lastKnownInputs.get(remotePlayerId);
                        if (predicted != null) {
                            InputCommand copy = new InputCommand(frameNumber, remotePlayerId);
                            copy.up = predicted.up; copy.down = predicted.down;
                            copy.left = predicted.left; copy.right = predicted.right;
                            copy.punch = predicted.punch; copy.kick = predicted.kick;
                            copy.special = predicted.special;
                            result.add(copy);
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * 检查并执行回滚
     */
    private void checkAndRollback(int currentFrame) {
        // 当收到远程确认输入时，检查是否与预测一致
        for (Map.Entry<Integer, InputBuffer> entry : remoteInputBuffers.entrySet()) {
            int remoteFrame = entry.getValue().getCurrentFrame();
            if (remoteFrame > confirmedFrame) {
                // 有新的确认帧
                InputCommand confirmedInput = entry.getValue().getInput(confirmedFrame + 1);
                InputCommand predictedInput = lastKnownInputs.get(entry.getKey());

                if (predictedInput != null && !predictedInput.equals(confirmedInput)) {
                    // 预测错误，需要回滚
                    int rollbackFrames = Math.min(currentFrame - confirmedFrame, ROLLBACK_MAX_FRAMES);
                    rollback(currentFrame - rollbackFrames);
                }

                confirmedFrame = Math.max(confirmedFrame, remoteFrame);
            }
        }
    }

    /**
     * 执行回滚
     */
    private void rollback(int targetFrame) {
        System.out.println("回滚到帧: " + targetFrame);

        // 1. 回滚游戏世界状态
        gameWorld.rollbackTo(targetFrame);

        // 2. 回滚输入缓冲区
        localInputBuffer.rollbackTo(targetFrame);
        for (InputBuffer buffer : remoteInputBuffers.values()) {
            buffer.rollbackTo(targetFrame);
        }

        // 3. 从目标帧重新模拟
        for (int frame = targetFrame; frame < gameWorld.getCurrentFrame(); frame++) {
            List<InputCommand> inputs = gatherInputs(frame);
            gameWorld.update(inputs, frame);
        }
    }

    /**
     * 接收远程输入（由网络层调用）
     */
    public void receiveRemoteInput(InputCommand remoteCmd) {
        int playerId = remoteCmd.playerId;
        remoteInputBuffers.computeIfAbsent(playerId, k -> new InputBuffer(120))
                .addInput(remoteCmd);
    }
}