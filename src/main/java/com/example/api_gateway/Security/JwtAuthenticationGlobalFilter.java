package com.example.api_gateway.Security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * ==========================================
 * JWT AUTHENTICATION GLOBAL FILTER (Gateway)
 * ==========================================
 * 
 * ARKITEKTUR:
 * Client (JWT token) → Gateway (validering) → Services (headers från Gateway)
 * 
 * DETTA FILTER GÖR:
 * 1. Tar emot request från klient med Authorization: Bearer <token>
 * 2. Validerar JWT token (signatur + expiration)
 * 3. Extraherar userId, email, role från token
 * 4. Sätter headers för downstream services:
 * - X-User-Id: userId
 * - X-User-Email: email
 * - X-User-Role: role (USER eller ADMIN)
 * 5. Tar bort Authorization header (services behöver inte JWT)
 * 
 * SÄKERHET:
 * - Tar bort X-User-* headers från inkommande requests (förhindrar header
 * injection)
 * - Endast Gateway får sätta dessa headers
 * - Services litar 100% på dessa headers
 * 
 * PUBLIC ENDPOINTS:
 * - /api/auth/** - magic link login
 * - /api/users/register - user registration
 * Dessa behöver INTE JWT token
 * 
 * ORDNING:
 * Detta filter körs FÖRST (Ordered.HIGHEST_PRECEDENCE + 1)
 * Före andra filters och routing
 */
@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationGlobalFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    // Public endpoints som inte kräver JWT (alla HTTP metoder)
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
            "/api/auth/login",
            "/api/auth/verify",
            "/api/auth/verify-jwt",
            "/api/auth/tokens", // POST /api/auth/tokens - verify and get JWT
            "/api/quizzes/images", // Quiz images - accessible without JWT
            "/api/exams/images", // Exam images - accessible without JWT
            "/api/admin/auth/login", // AdminService - Admin login
            "/api/admin/auth/health", // AdminService - Health check
            "/api/webhooks/swish" // PaymentService - Swish callback
    );

    // Public POST endpoints (endast POST, övriga metoder kräver JWT)
    private static final List<String> PUBLIC_POST_PATHS = Arrays.asList(
            "/api/users" // POST /api/users - User registration
    );

    // Public GET endpoints (read-only, no JWT needed)
    private static final List<String> PUBLIC_GET_PATHS = Arrays.asList();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        log.debug("🌐 [Gateway JWT Filter] Processing: {} {}", request.getMethod(), path);

        // STEG 1: Kolla om endpoint är public (alla metoder)
        if (isPublicPath(path)) {
            log.debug("✅ Public endpoint - No JWT required: {}", path);
            // Ta bort potentiella X-User-* headers för säkerhet (header injection attack)
            return chain.filter(stripUserHeaders(exchange));
        }

        // STEG 2: Kolla om endpoint är public för POST requests
        if (isPublicPostPath(path, request.getMethod())) {
            log.debug("✅ Public POST endpoint - No JWT required: {} {}", request.getMethod(), path);
            return chain.filter(stripUserHeaders(exchange));
        }

        // STEG 3: Kolla om endpoint är public för GET requests
        if (isPublicGetPath(path, request.getMethod())) {
            log.debug("✅ Public GET endpoint - No JWT required: {} {}", request.getMethod(), path);
            return chain.filter(stripUserHeaders(exchange));
        }

        // STEG 3: Hämta Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("❌ Missing or invalid Authorization header for: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // STEG 4: Extrahera JWT token
        String token = authHeader.substring(7); // Ta bort "Bearer "

        try {
            // STEG 5: Validera JWT token
            if (!jwtUtil.validateToken(token)) {
                log.warn("❌ Invalid JWT token for: {}", path);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // STEG 6: Extrahera user info från token
            Long userId = jwtUtil.extractUserId(token);
            String userEmail = jwtUtil.extractEmail(token);
            String userRole = jwtUtil.extractRole(token);

            log.debug("JWT validated: userId={} role={} path={}", userId, userRole, path);

            // STEG 7: Skapa muterad request med nya headers för downstream services
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    // Ta bort gamla X-User-* headers (säkerhet)
                    .headers(headers -> {
                        headers.remove("X-User-Id");
                        headers.remove("X-User-Email");
                        headers.remove("X-User-Role");
                    })
                    // Lägg till nya headers från JWT
                    .header("X-User-Id", String.valueOf(userId))
                    .header("X-User-Email", userEmail)
                    .header("X-User-Role", userRole)
                    // Ta bort Authorization header (services behöver inte JWT)
                    .headers(headers -> headers.remove(HttpHeaders.AUTHORIZATION))
                    .build();

            // STEG 8: Fortsätt med muterad request
            ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
            return chain.filter(mutatedExchange);

        } catch (Exception e) {
            log.error("❌ JWT validation error for {}: {}", path, e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    /**
     * Kollar om path är en public endpoint (alla HTTP metoder)
     */
    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * Kollar om path är en public endpoint för POST requests
     */
    private boolean isPublicPostPath(String path, HttpMethod method) {
        if (method != HttpMethod.POST) {
            return false; // Endast POST är public
        }
        return PUBLIC_POST_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * Kollar om path är en public endpoint för GET requests
     */
    private boolean isPublicGetPath(String path, HttpMethod method) {
        if (method != HttpMethod.GET) {
            return false; // Endast GET är public
        }
        return PUBLIC_GET_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * Tar bort X-User-* headers från request (security)
     * Förhindrar att externa klienter injekterar dessa headers
     */
    private ServerWebExchange stripUserHeaders(ServerWebExchange exchange) {
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Email");
                    headers.remove("X-User-Role");
                })
                .build();

        return exchange.mutate().request(mutatedRequest).build();
    }

    /**
     * Filter ordning - kör detta FÖRST (efter logging)
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
