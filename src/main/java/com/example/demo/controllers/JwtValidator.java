package com.example.demo.controllers;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.security.Key;

@Service
public class JwtValidator {

    // Тот же секретный ключ, что и на Node.js сервере
    @Value("${jwt.secret}")
    private String secret;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    // Проверить JWT и вернуть данные пользователя
    public Claims validateToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            throw new RuntimeException("Token expired");
        } catch (JwtException e) {
            throw new RuntimeException("Invalid token");
        }
    }

    // Просто проверить валидность
    public boolean isValid(String token) {
        try {
            validateToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Получить userId из токена
    public String getUserId(String token) {
        Claims claims = validateToken(token);
        Integer userIdInt = claims.get("id", Integer.class);
        return String.valueOf(userIdInt); // Преобразуем в String
        //return validateToken(token).get("id", String.class);
        //return validateToken(token).getSubject();
    }

    // Получить email из токена
    public String getEmail(String token) {
        System.out.print(token);
        return validateToken(token).get("email", String.class);
        //return validateToken(token).get("UserEmail", String.class);
    }
}