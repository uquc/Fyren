package com.Fyren.network;

import java.nio.ByteBuffer;

/**
 * 匹配请求包 - 客户端发送匹配请求到服务器
 * 携带玩家ID和当前MMR分数
 */
public class MatchRequestPacket extends Packet {
    public int playerId;      // 玩家ID
    public int playerRating;  // 玩家当前MMR（隐藏分）
    public int presetOrdinal; // 角色预设(FighterPreset.ordinal), 1=TAKESHI

    public MatchRequestPacket(int sequence, int playerId, int playerRating, int presetOrdinal) {
        this.type = Type.MATCH_REQ;
        this.sequence = sequence;
        this.playerId = playerId;
        this.playerRating = playerRating;
        this.presetOrdinal = presetOrdinal;
    }

    /** 向后兼容 — 默认TAKESHI */
    public MatchRequestPacket(int sequence, int playerId, int playerRating) {
        this(sequence, playerId, playerRating, 1);
    }

    /**
     * 从缓冲区反序列化匹配请求包
     */
    public static MatchRequestPacket fromBuffer(ByteBuffer buf, int sequence) {
        int playerId = buf.getInt();
        int rating = buf.getInt();
        int presetOrdinal = buf.getInt();
        return new MatchRequestPacket(sequence, playerId, rating, presetOrdinal);
    }

    @Override
    protected void writePayload(ByteBuffer buf) {
        buf.putInt(playerId);
        buf.putInt(playerRating);
        buf.putInt(presetOrdinal);
    }

    @Override
    protected int getPayloadSize() {
        return 12; // 4字节(playerId) + 4字节(rating) + 4字节(presetOrdinal)
    }
}
