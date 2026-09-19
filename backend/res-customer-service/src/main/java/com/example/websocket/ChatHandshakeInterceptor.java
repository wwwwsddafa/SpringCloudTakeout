package com.example.websocket;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@Slf4j
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        log.info("收到WebSocket握手请求: uri={}", request.getURI());
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();

            String userId = httpRequest.getParameter("userId");
            String role = httpRequest.getParameter("role");
            String userName = httpRequest.getParameter("userName");

            if (userId == null || userId.isEmpty()) {
                log.warn("WebSocket握手失败: userId为空");
                return false;
            }

            if (userName == null || userName.isEmpty()) {
                userName = "用户_" + userId.substring(0, Math.min(8, userId.length()));
            }

            attributes.put("userId", userId);
            attributes.put("role", role != null ? role : "user");
            attributes.put("userName", userName);
            log.info("WebSocket握手成功: userId={}, role={}", userId, attributes.get("role"));
            return true;
        }
        log.error("WebSocket握手失败: request类型不是ServletServerHttpRequest");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.error("WebSocket握手后处理异常: {}", exception.getMessage(), exception);
        } else {
            log.info("WebSocket握手完成: uri={}", request.getURI());
        }
    }
}