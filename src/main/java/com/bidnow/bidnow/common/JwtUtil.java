package com.bidnow.bidnow.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类：生成令牌 + 解析令牌。
 *
 * 令牌结构（三部分，用点号分隔）：
 *   Header.Payload.Signature
 *
 * Header   = {"alg":"HS256"}  → 算法类型
 * Payload  = {"sub":"1","username":"张三","exp":1722350000}  → 数据
 * Signature = 对前面两部分的签名，防篡改
 */
@Component
public class JwtUtil {

    /** 签名密钥。至少 256 位即 32 个字符，HS256 算法的要求。 */
    private static final String SECRET = "BidNow@2026_SecretKey_MustBe256Bits!";

    /** 令牌有效期：7 天 */
    private static final long EXPIRE_MS = 7 * 24 * 60 * 60 * 1000L;

    /** 把密钥字符串转成加密算法需要的 Key 对象 */
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    /**
     * 生成 JWT 令牌。
     * @param userId   用户 ID
     * @param username 用户名
     * @return JWT 字符串，形如 "eyJhbG...eyJzdWI...签名"
     */
    public String generate(Long userId, String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId.toString())              // subject = userId
                .claim("username", username)              // 自定义字段
                .issuedAt(new Date(now))                  // 签发时间
                .expiration(new Date(now + EXPIRE_MS))    // 过期时间
                .signWith(key)                            // 用密钥签名
                .compact();                               // 返回字符串
    }

    /**
     * 解析并验证 JWT 令牌。
     * @param token JWT 字符串
     * @return Payload 里的数据（Claims）；签名不对/过期/格式错误 → null
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)           // 验签
                    .build()
                    .parseSignedClaims(token)  // 解析 + 自动检查过期
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    /** 从令牌里拿 userId */
    public Long getUserId(String token) {
        Claims claims = parse(token);
        if (claims == null) return null;
        return Long.valueOf(claims.getSubject());
    }
}
