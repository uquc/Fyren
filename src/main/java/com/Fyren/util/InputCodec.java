package com.Fyren.util;

import com.Fyren.network.InputPacket;
import com.Fyren.sync.InputCommand;

/**
 * 输入编解码器 — 将InputCommand与网络传输的InputPacket互相转换
 *
 * 编码方案（紧凑位标志）：
 *   bit 0: up
 *   bit 1: down
 *   bit 2: left
 *   bit 3: right
 *   bit 4: punch
 *   bit 5: kick
 *   bit 6: special
 *   bit 7-31: reserved
 */
public final class InputCodec {

    // 位标志常量
    private static final int FLAG_UP      = 1 << 0;  // 0x01
    private static final int FLAG_DOWN    = 1 << 1;  // 0x02
    private static final int FLAG_LEFT    = 1 << 2;  // 0x04
    private static final int FLAG_RIGHT   = 1 << 3;  // 0x08
    private static final int FLAG_PUNCH   = 1 << 4;  // 0x10
    private static final int FLAG_KICK    = 1 << 5;  // 0x20
    private static final int FLAG_SPECIAL = 1 << 6;  // 0x40

    private InputCodec() {} // 工具类，禁止实例化

    /**
     * 将InputCommand编码为InputPacket用于网络传输
     *
     * @param cmd      输入指令
     * @param sequence 包序列号
     * @return 编码后的InputPacket
     */
    public static InputPacket encode(InputCommand cmd, int sequence) {
        int flags = 0;
        if (cmd.up)      flags |= FLAG_UP;
        if (cmd.down)    flags |= FLAG_DOWN;
        if (cmd.left)    flags |= FLAG_LEFT;
        if (cmd.right)   flags |= FLAG_RIGHT;
        if (cmd.punch)   flags |= FLAG_PUNCH;
        if (cmd.kick)    flags |= FLAG_KICK;
        if (cmd.special) flags |= FLAG_SPECIAL;

        // 将playerId和frameNumber打包到timestamp中
        // 高32位: playerId, 低32位: frameNumber
        long packedMeta = ((long) cmd.playerId << 32) | (cmd.frameNumber & 0xFFFFFFFFL);

        return new InputPacket(sequence, flags, packedMeta);
    }

    /**
     * 将InputPacket解码为InputCommand
     *
     * @param packet 网络输入包
     * @return 解码后的InputCommand
     */
    public static InputCommand decode(InputPacket packet) {
        int flags = packet.inputFlags;

        // 从timestamp中解包playerId和frameNumber
        int playerId = (int) (packet.timestamp >> 32);
        int frameNumber = (int) (packet.timestamp & 0xFFFFFFFFL);

        InputCommand cmd = new InputCommand(frameNumber, playerId);
        cmd.up      = (flags & FLAG_UP) != 0;
        cmd.down    = (flags & FLAG_DOWN) != 0;
        cmd.left    = (flags & FLAG_LEFT) != 0;
        cmd.right   = (flags & FLAG_RIGHT) != 0;
        cmd.punch   = (flags & FLAG_PUNCH) != 0;
        cmd.kick    = (flags & FLAG_KICK) != 0;
        cmd.special = (flags & FLAG_SPECIAL) != 0;

        return cmd;
    }

    /**
     * 直接将InputCommand编码为位标志int（不含playerId/frameNumber）
     * 用于需要自定义包头的情况
     */
    public static int toFlags(InputCommand cmd) {
        int flags = 0;
        if (cmd.up)      flags |= FLAG_UP;
        if (cmd.down)    flags |= FLAG_DOWN;
        if (cmd.left)    flags |= FLAG_LEFT;
        if (cmd.right)   flags |= FLAG_RIGHT;
        if (cmd.punch)   flags |= FLAG_PUNCH;
        if (cmd.kick)    flags |= FLAG_KICK;
        if (cmd.special) flags |= FLAG_SPECIAL;
        return flags;
    }

    /**
     * 从位标志int解码为InputCommand
     */
    public static InputCommand fromFlags(int flags, int frameNumber, int playerId) {
        InputCommand cmd = new InputCommand(frameNumber, playerId);
        cmd.up      = (flags & FLAG_UP) != 0;
        cmd.down    = (flags & FLAG_DOWN) != 0;
        cmd.left    = (flags & FLAG_LEFT) != 0;
        cmd.right   = (flags & FLAG_RIGHT) != 0;
        cmd.punch   = (flags & FLAG_PUNCH) != 0;
        cmd.kick    = (flags & FLAG_KICK) != 0;
        cmd.special = (flags & FLAG_SPECIAL) != 0;
        return cmd;
    }
}
