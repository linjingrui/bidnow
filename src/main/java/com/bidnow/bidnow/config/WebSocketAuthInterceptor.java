package com.bidnow.bidnow.config;

import com.bidnow.bidnow.common.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * WebSocket JWT 认证拦截器。
 *
 * WebSocket 的认证和 HTTP 不同：
 *   - HTTP 每个请求都带 Authorization Header → LoginInterceptor 每次解析
 *   - WebSocket 只在 STOMP CONNECT 帧做一次认证 → 成功后 Principal 绑定到 session
 *
 * 后续 SimpMessagingTemplate.convertAndSendToUser(userId, ...) 靠
 * Principal.getName() 匹配目标用户，Spring 自动路由到正确 session。
 */
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, StompHeaderAccessor.class);

        // 只在 STOMP CONNECT 帧时校验（其他帧如 SUBSCRIBE/SEND/MESSAGE 不需要）
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // STOMP.js 的 connectHeaders 会放到 STOMP 帧的原生 Header 里
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new IllegalArgumentException("WebSocket 连接缺少有效的 Authorization Header");
            }

            String token = authHeader.substring(7);
            Long userId = jwtUtil.getUserId(token);
            if (userId == null) {
                throw new IllegalArgumentException("WebSocket Token 无效或已过期");
            }

            // 设置 STOMP session 的 Principal —— 这是最关键的一行
            // Principal.getName() 返回 userId 字符串，Spring 用它来路由
            // "/user/{name}/queue/..." → 匹配到的 session 才能收到消息
            accessor.setUser(new StompPrincipal(userId));
        }

        return message;
    }

    /**
     * 轻量 Principal 实现 —— 只存 userId。
     * getName() 返回 userId 字符串，作为 SimpMessagingTemplate 的用户路由 key。
     */
    public record StompPrincipal(Long userId) implements Principal {
        @Override
        public String getName() {
            return userId.toString();
        }
    }
}
