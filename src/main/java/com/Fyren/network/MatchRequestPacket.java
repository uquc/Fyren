package com.Fyren.network;

import java.nio.ByteBuffer;

/**
 * 匹配请求包 - 客户端发送匹配请求到服务器
 * 携带玩家ID和当前MMR分数
 */
public class MatchRequestPacket extends Packet {
    public int playerId;      // 玩家ID
    public int playerRating;  // 玩家当前MMR（隐藏分）

    public MatchRequestPacket(int sequence, int playerId, int playerRating) {
        this.type = Type.MATCH_REQ;
        this.sequence = sequence;
        this.playerId = playerId;
        this.playerRating = playerRating;
    }

    /**
     * 从缓冲区反序列化匹配请求包
     */
    public static MatchRequestPacket fromBuffer(ByteBuffer buf, int sequence) {
        int playerId = buf.getInt();
        int rating = buf.getInt();
        return new MatchRequestPacket(sequence, playerId, rating);
    }

    @Override
    protected void writePayload(ByteBuffer buf) {
        buf.putInt(playerId);
        buf.putInt(playerRating);
    }

    @Override
    protected int getPayloadSize() {
        return 8; // 4字节(playerId) + 4字节(rating)
    }
}
