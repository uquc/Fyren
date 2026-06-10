// UdpClient.java
package com.Fyren.network;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * UDP游戏客户端 - 负责发送操作指令和接收游戏状态
 */
public class UdpClient {
    private DatagramSocket socket;
    private InetSocketAddress serverAddress;
    private volatile boolean running = false;
    private ExecutorService receiveExecutor;

    // P2P 直连
    private volatile InetSocketAddress p2pAddress = null;
    private volatile boolean p2pActive = false;

    // 回调函数
    private Consumer<Packet> onPacketReceived;
    private Consumer<Exception> onError;

    // 重传相关
    private final ConcurrentHashMap<Integer, PendingPacket> pendingPackets = new ConcurrentHashMap<>();
    private ScheduledExecutorService retransmitScheduler;

    public UdpClient(String serverIp, int serverPort) throws SocketException {
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout(1000); // 1秒超时
        this.serverAddress = new InetSocketAddress(serverIp, serverPort);
        this.receiveExecutor = Executors.newSingleThreadExecutor();
        this.retransmitScheduler = Executors.newScheduledThreadPool(1);
    }

    public void start() {
        running = true;
        receiveExecutor.submit(this::receiveLoop);
        // 心跳任务
        retransmitScheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, 500, TimeUnit.MILLISECONDS);
        // 重传检查
        retransmitScheduler.scheduleAtFixedRate(this::checkRetransmit, 0, 50, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        running = false;
        socket.close();
        receiveExecutor.shutdown();
        retransmitScheduler.shutdown();
    }

    private void receiveLoop() {
        byte[] buffer = new byte[1024];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                byte[] data = new byte[packet.getLength()];
                System.arraycopy(buffer, 0, data, 0, packet.getLength());

                Packet gamePacket = Packet.deserialize(data);
                if (gamePacket != null && onPacketReceived != null) {
                    onPacketReceived.accept(gamePacket);
                }
            } catch (SocketTimeoutException e) {
                // 超时正常，继续循环
            } catch (IOException e) {
                if (running && onError != null) onError.accept(e);
            }
        }
    }

    /**
     * 发送可靠UDP包（带重传机制）
     */
    public void sendReliable(Packet packet) {
        try {
            byte[] data = packet.serialize();
            DatagramPacket dp = new DatagramPacket(data, data.length, serverAddress);
            socket.send(dp);

            // 加入待确认队列
            pendingPackets.put(packet.sequence, new PendingPacket(data, System.currentTimeMillis(), packet.sequence));
        } catch (IOException e) {
            if (onError != null) onError.accept(e);
        }
    }

    /**
     * 发送不可靠UDP包（始终发往服务器，心跳/匹配等）
     */
    public void sendUnreliable(Packet packet) {
        try {
            byte[] data = packet.serialize();
            DatagramPacket dp = new DatagramPacket(data, data.length, serverAddress);
            socket.send(dp);
        } catch (IOException e) {
            if (onError != null) onError.accept(e);
        }
    }

    /**
     * 发送输入到对手 — P2P 激活时直连，否则走服务器中继
     */
    public void sendInputToOpponent(InputPacket packet) {
        InetSocketAddress target = p2pActive && p2pAddress != null ? p2pAddress : serverAddress;
        try {
            byte[] data = packet.serialize();
            DatagramPacket dp = new DatagramPacket(data, data.length, target);
            socket.send(dp);
        } catch (IOException e) {
            if (onError != null) onError.accept(e);
        }
    }

    /** 发送原始字节到指定地址（P2P 握手机制使用） */
    public void sendRaw(byte[] data, InetSocketAddress addr) {
        try {
            DatagramPacket dp = new DatagramPacket(data, data.length, addr);
            socket.send(dp);
        } catch (IOException e) {
            if (onError != null) onError.accept(e);
        }
    }

    /** 启用 P2P 直连 */
    public void enableP2P(InetSocketAddress addr) {
        this.p2pAddress = addr;
        this.p2pActive = true;
    }

    public boolean isP2pActive() { return p2pActive; }
    public InetSocketAddress getP2pAddress() { return p2pAddress; }

    private void sendHeartbeat() {
        HeartbeatPacket hb = new HeartbeatPacket(generateSequence());
        sendUnreliable(hb);
    }

    private void checkRetransmit() {
        long now = System.currentTimeMillis();
        for (PendingPacket pp : pendingPackets.values()) {
            if (now - pp.sendTime > 100) { // 100ms未确认则重传
                try {
                    DatagramPacket dp = new DatagramPacket(pp.data, pp.data.length, serverAddress);
                    socket.send(dp);
                    pp.sendTime = now;
                    pp.retryCount++;
                    if (pp.retryCount > 5) {
                        pendingPackets.remove(pp.sequence); // 放弃重传
                    }
                } catch (IOException ignored) {}
            }
        }
    }

    // 收到ACK时调用
    public void onAckReceived(int sequence) {
        pendingPackets.remove(sequence);
    }

    private static int sequenceGenerator = 0;
    private static synchronized int generateSequence() {
        return ++sequenceGenerator;
    }

    // 内部类：待确认包
    private static class PendingPacket {
        byte[] data;
        long sendTime;
        int retryCount = 0;
        int sequence;
        PendingPacket(byte[] data, long sendTime, int sequence) {
            this.data = data;
            this.sendTime = sendTime;
            this.sequence = sequence;
        }
    }

    // Setter
    public void setOnPacketReceived(Consumer<Packet> onPacketReceived) {
        this.onPacketReceived = onPacketReceived;
    }
    public void setOnError(Consumer<Exception> onError) {
        this.onError = onError;
    }
}