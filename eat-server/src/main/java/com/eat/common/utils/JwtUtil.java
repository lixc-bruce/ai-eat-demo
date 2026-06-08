package com.eat.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    @Value("${eat.jwt.secret}")
    private String secret;

    @Value("${eat.jwt.expiration:604800000}")
    private long expiration;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(Long userId, String phone) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("phone", phone)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(key)
                .compact();
    }

    public Claims validate(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new JwtExpiredException("Token已过期");
        } catch (Exception e) {
            throw new JwtInvalidException("Token无效");
        }
    }

    public Long parseUserId(String token) {
        Claims claims = validate(token);
        return Long.parseLong(claims.getSubject());
    }

    public static class JwtExpiredException extends RuntimeException {
        public JwtExpiredException(String msg) { super(msg); }
    }

    public static class JwtInvalidException extends RuntimeException {
        public JwtInvalidException(String msg) { super(msg); }
    }
}
