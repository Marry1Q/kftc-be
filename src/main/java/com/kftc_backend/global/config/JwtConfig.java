package com.kftc_backend.global.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.time.Duration;

@Configuration
public class JwtConfig {
    
    // JWT 서명용 시크릿 키 (환경변수에서 읽어옴)
    @Value("${jwt.secret-key}")
    private String jwtSecret;
    
    @Bean
    public SecretKey jwtSecretKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }
    
    // Access Token 만료시간 (90일 = 7,776,000초)
    public static final long ACCESS_TOKEN_EXPIRY_SECONDS = 90L * 24 * 60 * 60;
    public static final Duration ACCESS_TOKEN_EXPIRY = Duration.ofSeconds(ACCESS_TOKEN_EXPIRY_SECONDS);
} 