package com.Fyren.network;

import java.nio.ByteBuffer;

/**
 * 比赛结果包 — 客户端上报比赛结果，服务器据此更新MMR。
 */
public class ResultPacket extends Packet {
    public int player1Id;
    public int player2Id;
    public int winnerId; // -1=平局, 否则为胜者ID

    public ResultPacket(int sequence, int player1Id, int player2Id, int winnerId) {
        this.type = Type.RESULT;
        this.sequence = sequence;
        this.player1Id = player1Id;
        this.player2Id = player2Id;
        this.winnerId = winnerId;
    }

    public static ResultPacket fromBuffer(ByteBuffer buf, int sequence) {
        int p1 = buf.getInt();
        int p2 = buf.getInt();
        int winner = buf.getInt();
        return new ResultPacket(sequence, p1, p2, winner);
    }

    @Override
    protected void writePayload(ByteBuffer buf) {
        buf.putInt(player1Id);
        buf.putInt(player2Id);
        buf.putInt(winnerId);
    }

    @Override
    protected int getPayloadSize() {
        return 12; // 4字节 * 3
    }
}
