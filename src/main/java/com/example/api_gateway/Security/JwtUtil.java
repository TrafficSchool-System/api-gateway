package com.example.api_gateway.Security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

/**
 * ==========================================
 * JWT UTILITY (Gateway Version)
 * ==========================================
 * 
 * ARKITEKTUR:
 * - Gateway validerar JWT tokens från externa klienter
 * - Extraherar userId, email, role från token
 * - Sätter headers för downstream services
 * 
 * VIKTIGT:
 * - JWT_SECRET måste vara SAMMA i Gateway och UserService
 * - UserService använder detta för att GENERERA tokens
 * - Gateway använder detta för att VALIDERA tokens
 * 
 * SÄKERHET:
 * - Secret key ska ALDRIG commitas till Git
 * - I produktion: Använd environment variable JWT_SECRET
 * - Använd stark secret (minst 256 bits för HS256)
 * - Secret läses från application.properties som har fallback till JWT_SECRET
 * env var
 */
@Component
public class JwtUtil {

    /**
     * JWT Secret läses från application.properties
     * 
     * application.properties innehåller:
     * jwt.secret=${JWT_SECRET:mySecretKey123456789012345678901234567890}
     * 
     * PRODUKTION: Sätt environment variable JWT_SECRET
     * UTVECKLING: Använder default från application.properties
     * 
     * VIKTIGT: Samma secret som UserService!
     */
    @Value("${jwt.secret}")
    private String secret;

    /**
     * Skapar signing key från secret
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * Validerar JWT token
     * Kollar:
     * 1. Signatur är korrekt
     * 2. Token inte har gått ut
     * 3. Token kan parsas
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);

            // Kolla expiration
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extraherar email (subject) från token
     */
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extraherar role från token
     */
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    /**
     * Extraherar userId från token
     */
    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }

    /**
     * Extraherar expiration date
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Generisk claim extractor
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extraherar alla claims från token
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Kollar om token har gått ut
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
}
