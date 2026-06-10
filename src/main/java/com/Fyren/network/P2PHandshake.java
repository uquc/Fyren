package com.Fyren.network;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * P2P UDP 打洞握手 — 匹配成功后异步执行。
 *
 * 原理：双方同时向对方公网地址发送 UDP 包（P2P_PING），
 * 利用路由器建立 NAT 映射。收到对方的 P2P_PING 后回复 P2P_PONG。
 * 收到 P2P_PONG 即表示打洞成功。
 *
 * 超时 2 秒未收到 P2P_PONG → 降级为服务器中继（isReady() 保持 false）。
 */
public class P2PHandshake {

    private static final int PUNCH_COUNT = 10;       // 打洞包数量
    private static final int PUNCH_INTERVAL_MS = 20; // 间隔
    private static final int TIMEOUT_MS = 2000;      // 总超时

    private final UdpClient udpClient;
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;

    public P2PHandshake(UdpClient udpClient) {
        this.udpClient = udpClient;
    }

    /** 启动异步握手（非阻塞） */
    public void start(InetSocketAddress opponentAddr) {
        if (running.getAndSet(true)) return;

        thread = new Thread(() -> {
            try {
                int seq = 0;
                long startMs = System.currentTimeMillis();

                // 发送打洞包
                for (int i = 0; i < PUNCH_COUNT && !ready.get(); i++) {
                    P2pPacket ping = new P2pPacket(seq++, Packet.Type.P2P_PING);
                    udpClient.sendRaw(ping.serialize(), opponentAddr);
                    Thread.sleep(PUNCH_INTERVAL_MS);
                }

                // 等待 P2P_PONG
                long deadline = startMs + TIMEOUT_MS;
                while (System.currentTimeMillis() < deadline && !ready.get()) {
                    Thread.sleep(50);
                }
            } catch (InterruptedException ignored) {
            }
            running.set(false);

            if (ready.get()) {
                System.out.println("[P2P] 打洞成功! 直连 " + opponentAddr);
            } else {
                System.out.println("[P2P] 打洞超时，降级服务器中继");
            }
        }, "P2P-handshake");
        thread.setDaemon(true);
        thread.start();
    }

    /** UdpClient 收到 P2P_PING 时调用 → 回复 P2P_PONG */
    public void onPingReceived(InetSocketAddress fromAddr) {
        P2pPacket pong = new P2pPacket(0, Packet.Type.P2P_PONG);
        udpClient.sendRaw(pong.serialize(), fromAddr);
    }

    /** UdpClient 收到 P2P_PONG 时调用 → 打洞成功 */
    public void onPongReceived() {
        ready.set(true);
    }

    public boolean isReady() { return ready.get(); }
    public boolean isRunning() { return running.get(); }
}
