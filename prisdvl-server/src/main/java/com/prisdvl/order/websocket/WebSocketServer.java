package com.prisdvl.order.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理端 WebSocket 服务：支付成功来单提醒、用户催单提醒。
 *
 * <p>Session 放在 ConcurrentHashMap 中，推送失败/连接异常时及时摘除，
 * 避免 HashMap 并发读写和僵尸会话；客户端可用 "ping"/"pong" 做心跳。</p>
 */
@Component
@ServerEndpoint("/ws/{sid}")
@Slf4j
public class WebSocketServer {

    /**
     * 在线会话：sid -> Session
     */
    private static final Map<String, Session> SESSION_MAP = new ConcurrentHashMap<>();

    private static final String PING = "ping";
    private static final String PONG = "pong";

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        SESSION_MAP.put(sid, session);
        log.info("WebSocket连接建立，客户端：{}，当前在线：{}", sid, SESSION_MAP.size());
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, Session session, @PathParam("sid") String sid) {
        if (PING.equalsIgnoreCase(message)) {
            try {
                // 心跳响应走异步，避免阻塞容器线程
                session.getAsyncRemote().sendText(PONG);
            } catch (Exception e) {
                log.warn("WebSocket心跳响应失败，客户端：{}", sid, e);
                removeSession(sid, session);
            }
            return;
        }
        log.debug("收到来自客户端：{} 的消息：{}", sid, message);
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose(Session session, @PathParam("sid") String sid) {
        removeSession(sid, session);
        log.info("WebSocket连接断开，客户端：{}，当前在线：{}", sid, SESSION_MAP.size());
    }

    /**
     * 连接异常：记录并摘除会话，防止把消息继续推给坏连接。
     */
    @OnError
    public void onError(Session session, Throwable error, @PathParam("sid") String sid) {
        log.warn("WebSocket连接异常，客户端：{}", sid, error);
        removeSession(sid, session);
        closeQuietly(session);
    }

    /**
     * 群发消息（管理端来单/催单提醒）。
     *
     * @param message JSON 字符串
     */
    public void sendToAllClient(String message) {
        SESSION_MAP.forEach((sid, session) -> {
            if (!session.isOpen()) {
                removeSession(sid, session);
                return;
            }
            try {
                // 同一 Session 的并发发送需要串行化，避免 IllegalStateException
                synchronized (session) {
                    session.getBasicRemote().sendText(message);
                }
            } catch (Exception e) {
                log.warn("WebSocket消息推送失败，移除会话，客户端：{}", sid, e);
                removeSession(sid, session);
                closeQuietly(session);
            }
        });
    }

    /**
     * 当前在线连接数，便于监控。
     */
    public int getOnlineCount() {
        return SESSION_MAP.size();
    }

    private void removeSession(String sid, Session session) {
        // 条件删除：同一 sid 重连后，旧连接的 onClose 不能把新会话删掉
        SESSION_MAP.remove(sid, session);
    }

    private void closeQuietly(Session session) {
        try {
            if (session != null && session.isOpen()) {
                session.close();
            }
        } catch (IOException e) {
            log.debug("关闭WebSocket会话失败", e);
        }
    }
}
