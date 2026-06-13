package com.Fyren.network.gwt;

import com.google.gwt.core.client.JavaScriptObject;

/**
 * GWT JSNI 封装浏览器原生 WebSocket API。
 * 用于 GWT/WebGL 客户端与服务端的 WebSocket 通信。
 */
public class GwtWebSocket {

    public interface Callback {
        void onOpen();
        void onMessage(byte[] data);
        void onClose(int code, String reason);
        void onError(String message);
    }

    private JavaScriptObject ws;
    private final Callback callback;
    private final String url;

    public GwtWebSocket(String url, Callback callback) {
        this.url = url;
        this.callback = callback;
    }

    /** 打开 WebSocket 连接 */
    public native void connect() /*-{
        var self = this;
        var ws = new WebSocket(this.@com.Fyren.network.gwt.GwtWebSocket::url);
        ws.binaryType = "arraybuffer";

        ws.onopen = function() {
            self.@com.Fyren.network.gwt.GwtWebSocket::onOpen()();
        };

        ws.onmessage = function(event) {
            if (event.data instanceof ArrayBuffer) {
                var data = new Int8Array(event.data);
                self.@com.Fyren.network.gwt.GwtWebSocket::onMessage([B)(data);
            }
        };

        ws.onclose = function(event) {
            self.@com.Fyren.network.gwt.GwtWebSocket::onClose(ILjava/lang/String;)(event.code || 0, event.reason || "");
        };

        ws.onerror = function() {
            self.@com.Fyren.network.gwt.GwtWebSocket::onError(Ljava/lang/String;)("WebSocket error");
        };

        this.@com.Fyren.network.gwt.GwtWebSocket::ws = ws;
    }-*/;

    /** 发送二进制数据 */
    public native void send(byte[] data) /*-{
        var ws = this.@com.Fyren.network.gwt.GwtWebSocket::ws;
        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(new Uint8Array(data).buffer);
        }
    }-*/;

    /** 关闭连接 */
    public native void close() /*-{
        var ws = this.@com.Fyren.network.gwt.GwtWebSocket::ws;
        if (ws) {
            ws.onclose = null;
            ws.close();
        }
    }-*/;

    /** 获取就绪状态 (0=CONNECTING, 1=OPEN, 2=CLOSING, 3=CLOSED) */
    public native int getReadyState() /*-{
        var ws = this.@com.Fyren.network.gwt.GwtWebSocket::ws;
        return ws ? ws.readyState : 3;
    }-*/;

    // JSNI 回调桥接方法

    private void onOpen() {
        callback.onOpen();
    }

    private void onMessage(byte[] data) {
        if (data == null || data.length == 0) return;
        callback.onMessage(data);
    }

    private void onClose(int code, String reason) {
        callback.onClose(code, reason);
    }

    private void onError(String message) {
        callback.onError(message);
    }

    public boolean isOpen() {
        return getReadyState() == 1;
    }
}
