package com.Fyren.sync;
/**
 * 输入指令 - 每帧收集的玩家操作
 * 注意：所有字段必须是确定性的，不能有随机值
 */
public class InputCommand {
    public int frameNumber;      // 帧号
    public int playerId;         // 玩家ID
    public boolean up;
    public boolean down;
    public boolean left;
    public boolean right;
    public boolean punch;
    public boolean kick;
    public boolean special;

    public InputCommand(int frameNumber, int playerId) {
        this.frameNumber = frameNumber;
        this.playerId = playerId;
    }

    // 判断是否为空操作
    public boolean isEmpty() {
        return !up && !down && !left && !right && !punch && !kick && !special;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof InputCommand)) return false;
        InputCommand other = (InputCommand) o;
        return frameNumber == other.frameNumber &&
                playerId == other.playerId &&
                up == other.up && down == other.down &&
                left == other.left && right == other.right &&
                punch == other.punch && kick == other.kick &&
                special == other.special;
    }
}