package com.example.configs;

import com.example.web.vo.JwtUserInfo;
import com.example.web.vo.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
@RefreshScope
public class JwtTokenProvider {

    @Value("${jwt.secret:defaultSecretKeyForJwtTokenGeneration2024}")
    private String secret;

    @Value("${jwt.expiration:7200}")
    private long expiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(JwtUserInfo userInfo) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration * 1000);

        return Jwts.builder()
                .subject(userInfo.getUserId())
                .claim("username", userInfo.getUsername())
                .claim("role", userInfo.getRole().name())
                .id(userInfo.getJti())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public JwtUserInfo parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return JwtUserInfo.builder()
                .userId(claims.getSubject())
                .username(claims.get("username", String.class))
                .role(UserRole.fromCode(claims.get("role", String.class)))
                .jti(claims.getId())
                .build();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SignatureException e) {
            log.warn("JWT签名无效: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT格式错误: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("JWT已过期: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("不支持的JWT: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT参数为空: {}", e.getMessage());
        }
        return false;
    }
}