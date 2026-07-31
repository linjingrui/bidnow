package com.bidnow.bidnow.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket 配置。
 *
 * 三个核心职责：
 *   1. 注册 WebSocket 端点（/ws）
 *   2. 配置消息代理（客户端订阅 /topic /queue，服务端路由 /app）
 *   3. 注册认证拦截器（JWT 校验）
 *
 * /user 前缀是 Spring 的"用户目标"机制：
 *   服务端 convertAndSendToUser("1", "/queue/notifications", msg)
 *   → Spring 自动转换为 /user/1/queue/notifications
 *   → 只有 Principal.getName() == "1" 的 session 收到
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // enableSimpleBroker：内置内存消息代理，处理客户端订阅的 destination 前缀
        // /topic  → 广播消息（如"系统公告"）
        // /queue  → 点对点消息（如"用户通知"），和 /user 配合变成 /user/{id}/queue/xxx
        registry.enableSimpleBroker("/topic", "/queue");

        // setApplicationDestinationPrefixes：客户端发送消息时的前缀
        // 客户端 send("/app/chat", msg) → @MessageMapping("/chat") 处理
        registry.setApplicationDestinationPrefixes("/app");

        // setUserDestinationPrefix：用户目标前缀
        // convertAndSendToUser("1", "/queue/notifications", msg)
        // → 实际 destination = /user/1/queue/notifications
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // /ws 是 WebSocket 握手端点
        // withSockJS() 提供降级：浏览器不支持 WebSocket 时用 HTTP 长轮询模拟
        // setAllowedOriginPatterns("*") 允许跨域（Vite dev server 端口不同）
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 在 STOMP 消息进入时先经过 JWT 认证拦截器
        registration.interceptors(webSocketAuthInterceptor);
    }
}
