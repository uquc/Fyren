package com.Fyren.network;

import java.nio.ByteBuffer;

/**
 * 匹配结果包 - 服务器通知客户端匹配结果
 * 包含对手信息和连接方式
 */
public class MatchResponsePacket extends Packet {
    // 匹配状态
    public static final int STATUS_MATCHED = 0;    // 匹配成功
    public static final int STATUS_WAITING = 1;    // 等待中
    public static final int STATUS_CANCELLED = 2;  // 匹配取消
    public static final int STATUS_ERROR = 3;      // 匹配错误

    public int matchStatus;        // 匹配状态码
    public int opponentId;         // 对手玩家ID（匹配成功时有效）
    public int opponentRating;     // 对手MMR（匹配成功时有效）
    public String opponentAddress; // 对手IP地址
    public int opponentPort;       // 对手UDP端口

    public MatchResponsePacket(int sequence, int matchStatus, int opponentId,
                               int opponentRating, String opponentAddress, int opponentPort) {
        this.type = Type.MATCH_RES;
        this.sequence = sequence;
        this.matchStatus = matchStatus;
        this.opponentId = opponentId;
        this.opponentRating = opponentRating;
        this.opponentAddress = opponentAddress;
        this.opponentPort = opponentPort;
    }

    /**
     * 创建一个"等待中"的响应
     */
    public static MatchResponsePacket waiting(int sequence) {
        return new MatchResponsePacket(sequence, STATUS_WAITING, 0, 0, "", 0);
    }

    /**
     * 从缓冲区反序列化匹配结果包
     */
    public static MatchResponsePacket fromBuffer(ByteBuffer buf, int sequence) {
        int status = buf.getInt();
        int opponentId = buf.getInt();
        int opponentRating = buf.getInt();

        // 读取字符串（IP地址）
        int addrLen = buf.getInt();
        byte[] addrBytes = new byte[addrLen];
        buf.get(addrBytes);
        String address = new String(addrBytes);

        int port = buf.getInt();
        return new MatchResponsePacket(sequence, status, opponentId, opponentRating, address, port);
    }

    @Override
    protected void writePayload(ByteBuffer buf) {
        buf.putInt(matchStatus);
        buf.putInt(opponentId);
        buf.putInt(opponentRating);

        // 写入字符串（IP地址）
        byte[] addrBytes = opponentAddress != null ? opponentAddress.getBytes() : new byte[0];
        buf.putInt(addrBytes.length);
        buf.put(addrBytes);

        buf.putInt(opponentPort);
    }

    @Override
    protected int getPayloadSize() {
        int addrLen = opponentAddress != null ? opponentAddress.getBytes().length : 0;
        return 16 + addrLen; // 4*3(int) + 4(addrLen) + addrBytes + 4(port)
    }
}
