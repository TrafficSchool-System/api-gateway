package com.example.api_gateway.Security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * ==========================================
 * SUBSCRIPTION VALIDATION FILTER (Gateway)
 * ==========================================
 * 
 * ARKITEKTUR - CENTRALISERAD SUBSCRIPTION-KONTROLL:
 * Client → JWT Filter → Subscription Filter (detta) → Quiz/Exam Services
 * 
 * DETTA FILTER GÖR:
 * 1. Kollar om requesten går till skyddade endpoints (quiz, exam)
 * 2. Hämtar userId från X-User-Id header (satt av
 * JwtAuthenticationGlobalFilter)
 * 3. Anropar UserService för att kolla hasActiveSubscription
 * 4. Blockerar med 403 FORBIDDEN om användaren saknar aktivt abonnemang
 * 5. Släpper igenom om användaren har giltigt abonnemang
 * 
 * SKYDDADE ENDPOINTS:
 * - /api/quiz/** - Quiz endpoints (kräver subscription)
 * - /api/exam/** - Exam endpoints (kräver subscription)
 * 
 * UNDANTAG (kräver INTE subscription):
 * - Public endpoints (auth, registration)
 * - Admin endpoints (/api/admin/**)
 * - Static resources (/api/quiz/images)
 * 
 * ORDNING:
 * Detta filter körs EFTER JwtAuthenticationGlobalFilter (HIGHEST_PRECEDENCE +
 * 2)
 * så att X-User-Id header redan är satt.
 * 
 * FÖRDELAR MED GATEWAY-LEVEL VALIDATION:
 * ✅ Centraliserad logik (DRY - Don't Repeat Yourself)
 * ✅ Ingen kod-duplikering i varje microservice
 * ✅ Enkel att underhålla och uppdatera
 * ✅ Bättre performance (EN kontroll för alla services)
 */
@Component
public class SubscriptionValidationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionValidationFilter.class);

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Value("${service.api.key}")
    private String serviceApiKey;

    @Value("${services.user-service.url:http://USER-SERVICE}")
    private String userServiceUrl;

    // ==========================================
    // KONFIGURATION - SKYDDADE ENDPOINTS
    // ==========================================

    /**
     * Endpoints som KRÄVER aktivt abonnemang
     * Alla requests till dessa paths kommer att valideras
     */
    private static final List<String> SUBSCRIPTION_REQUIRED_PATHS = Arrays.asList(
            "/api/quiz", // Quiz endpoints
            "/api/exam" // Exam endpoints
    );

    /**
     * Endpoints som INTE kräver subscription (undantag)
     * Exempel: statiska bilder, public data, admin-endpoints
     */
    private static final List<String> SUBSCRIPTION_EXEMPT_PATHS = Arrays.asList(
            "/api/quiz/images", // Statiska quiz-bilder (public)
            "/api/admin" // Admin endpoints (admin har alltid access)
    );

    // ==========================================
    // FILTER LOGIC
    // ==========================================

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        log.debug("🔒 [Subscription Filter] Processing: {}", path);

        // STEG 1: Kolla om endpoint kräver subscription
        if (!requiresSubscription(path)) {
            log.debug("✅ Path does not require subscription: {}", path);
            return chain.filter(exchange);
        }

        // STEG 2: Hämta userId från header (satt av JwtAuthenticationGlobalFilter)
        String userIdHeader = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        String userRole = exchange.getRequest().getHeaders().getFirst("X-User-Role");

        if (userIdHeader == null) {
            // Detta borde aldrig hända (JWT-filter körs först)
            log.error("❌ X-User-Id header missing - JWT filter not executed?");
            return unauthorizedResponse(exchange, "Authentication required");
        }

        // STEG 3: Admin har alltid access (skip subscription check)
        if ("ADMIN".equals(userRole)) {
            log.info("✅ Admin access granted - skipping subscription check for path: {}", path);
            return chain.filter(exchange);
        }

        Long userId = Long.parseLong(userIdHeader);
        log.info("🔍 Checking subscription for userId={}, path={}", userId, path);

        // STEG 4: Anropa UserService för att kolla subscription
        return checkSubscription(userId)
                .flatMap(hasSubscription -> {
                    if (hasSubscription) {
                        log.info("✅ Subscription valid - allowing access to: {}", path);
                        return chain.filter(exchange);
                    } else {
                        log.warn("⛔ FORBIDDEN - No active subscription for userId={}, path={}", userId, path);
                        return forbiddenResponse(exchange);
                    }
                })
                .onErrorResume(error -> {
                    log.error("❌ Error checking subscription for userId={}: {}", userId, error.getMessage());
                    // Vid fel, neka access (fail-secure)
                    return serverErrorResponse(exchange, "Failed to validate subscription");
                });
    }

    // ==========================================
    // PRIVATA HJÄLPMETODER
    // ==========================================

    /**
     * KOLLA OM PATH KRÄVER SUBSCRIPTION
     * 
     * @param path Request path
     * @return true om path kräver subscription, false annars
     */
    private boolean requiresSubscription(String path) {
        // Kolla först om det är ett undantag
        if (SUBSCRIPTION_EXEMPT_PATHS.stream().anyMatch(path::startsWith)) {
            return false;
        }

        // Kolla sedan om det är en skyddad path
        return SUBSCRIPTION_REQUIRED_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * ANROPA USERSERVICE internal/users/{userId}/subscription-status
     * 
     * Detta är en dedikerad lightweight endpoint designad för Gateway.
     * Returnerar endast userId + hasActiveSubscription boolean.
     * 
     * SÄKERHET: Använder X-Internal-API-Key header för authentication.
     * Gateway är en betrodd internal service med ROLE_INTERNAL_SERVICE.
     * 
     * @param userId User ID att kolla
     * @return Mono<Boolean> - true om användaren har aktivt abonnemang
     */
    private Mono<Boolean> checkSubscription(Long userId) {
        String uri = userServiceUrl + "/api/internal/users/{userId}/subscription-status";
        log.debug("🔗 Calling UserService at: {}", uri);

        return webClientBuilder.build()
                .get()
                .uri(uri, userId)
                .header("X-Internal-API-Key", serviceApiKey)
                .retrieve()
                .bodyToMono(UserSubscriptionResponse.class)
                .map(UserSubscriptionResponse::isHasActiveSubscription)
                .doOnSuccess(hasSubscription -> log
                        .debug("UserService response for userId={}: hasActiveSubscription={}", userId, hasSubscription))
                .onErrorResume(error -> {
                    log.error("Failed to call UserService for userId={}: {}", userId, error.getMessage());
                    return Mono.just(false); // Fail-secure: neka om UserService inte svarar
                });
    }

    /**
     * RETURNERA 403 FORBIDDEN RESPONSE
     */
    private Mono<Void> forbiddenResponse(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String jsonResponse = """
                {
                    "error": "Forbidden",
                    "message": "Aktivt abonnemang krävs för att komma åt detta innehåll",
                    "code": "SUBSCRIPTION_REQUIRED"
                }
                """;

        return exchange.getResponse()
                .writeWith(Mono.just(
                        exchange.getResponse().bufferFactory().wrap(jsonResponse.getBytes())));
    }

    /**
     * RETURNERA 401 UNAUTHORIZED RESPONSE
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String jsonResponse = String.format("""
                {
                    "error": "Unauthorized",
                    "message": "%s"
                }
                """, message);

        return exchange.getResponse()
                .writeWith(Mono.just(
                        exchange.getResponse().bufferFactory().wrap(jsonResponse.getBytes())));
    }

    /**
     * RETURNERA 500 SERVER ERROR RESPONSE
     */
    private Mono<Void> serverErrorResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String jsonResponse = String.format("""
                {
                    "error": "Internal Server Error",
                    "message": "%s"
                }
                """, message);

        return exchange.getResponse()
                .writeWith(Mono.just(
                        exchange.getResponse().bufferFactory().wrap(jsonResponse.getBytes())));
    }

    /**
     * Filter ordning - körs EFTER JwtAuthenticationGlobalFilter
     * så att X-User-Id header redan är satt
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }

    // ==========================================
    // DTO FÖR USERSERVICE RESPONSE
    // ==========================================

    /**
     * DTO för att mappa UserService response
     * Matchar UserResponseDTO från UserService
     */
    private static class UserSubscriptionResponse {
        private Long id;
        private String email;
        private boolean hasActiveSubscription;

        // Getters and setters
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public boolean isHasActiveSubscription() {
            return hasActiveSubscription;
        }

        public void setHasActiveSubscription(boolean hasActiveSubscription) {
            this.hasActiveSubscription = hasActiveSubscription;
        }
    }
}
