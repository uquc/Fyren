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
    public String jwtToken;   // JWT access token（空字符串=未认证）

    public MatchRequestPacket(int sequence, int playerId, int playerRating, int presetOrdinal, String jwtToken) {
        this.type = Type.MATCH_REQ;
        this.sequence = sequence;
        this.playerId = playerId;
        this.playerRating = playerRating;
        this.presetOrdinal = presetOrdinal;
        this.jwtToken = jwtToken != null ? jwtToken : "";
    }

    /** 向后兼容 — 默认TAKESHI，无token */
    public MatchRequestPacket(int sequence, int playerId, int playerRating) {
        this(sequence, playerId, playerRating, 1, "");
    }

    /**
     * 从缓冲区反序列化匹配请求包
     */
    public static MatchRequestPacket fromBuffer(ByteBuffer buf, int sequence) {
        int playerId = buf.getInt();
        int rating = buf.getInt();
        int presetOrdinal = buf.getInt();
        // 向后兼容：旧客户端不发送 token 字段，payload 长度为 12
        String jwtToken = "";
        if (buf.hasRemaining()) {
            int tokenLen = buf.getInt();
            if (tokenLen > 0 && tokenLen <= 2048 && buf.remaining() >= tokenLen) {
                byte[] tokenBytes = new byte[tokenLen];
                buf.get(tokenBytes);
                jwtToken = new String(tokenBytes, java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return new MatchRequestPacket(sequence, playerId, rating, presetOrdinal, jwtToken);
    }

    @Override
    protected void writePayload(ByteBuffer buf) {
        buf.putInt(playerId);
        buf.putInt(playerRating);
        buf.putInt(presetOrdinal);
        byte[] tokenBytes = jwtToken.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.putInt(tokenBytes.length);
        if (tokenBytes.length > 0) {
            buf.put(tokenBytes);
        }
    }

    @Override
    protected int getPayloadSize() {
        byte[] tokenBytes = jwtToken.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return 12 + 4 + tokenBytes.length; // 原有12 + token长度字段4 + token字节
    }
}
