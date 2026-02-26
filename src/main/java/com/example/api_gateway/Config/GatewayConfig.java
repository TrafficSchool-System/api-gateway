package com.example.api_gateway.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import reactor.core.publisher.Mono;

/**
 * ==========================================
 * API GATEWAY CONFIGURATION
 * ==========================================
 * Centraliserad routing konfiguration för alla microservices.
 * 
 * ARKITEKTUR:
 * Client → API Gateway (8080) → Eureka → Microservices
 * 
 * ROUTING STRATEGI:
 * - Mest specifika routes FÖRST (/api/admin/**)
 * - Load balancing via Eureka (lb://)
 * - Global filters för logging och monitoring
 * - Timeout och retry konfiguration
 * 
 * SERVICES:
 * - admin-service (8084) - Admin endpoints
 * - user-service (8081) - Auth & Users
 * - payment-service (8085) - Payments & Packages
 * - quiz-service (8082) - Quizzes
 * - exam-service (8083) - Exams
 */
@Configuration
public class GatewayConfig {

        private static final Logger log = LoggerFactory.getLogger(GatewayConfig.class);

        /**
         * Route configuration med Eureka load balancing
         * 
         * VIKTIGT: Routes ordnade från mest specifik till minst specifik!
         */
        @Bean
        public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
                return builder.routes()
                                // =========================
                                // ADMIN SERVICE - HÖGST PRIORITET
                                // =========================
                                .route("admin-service", r -> r
                                                .path("/api/admin/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .retry(config -> config
                                                                                .setRetries(3)
                                                                                .setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)))
                                                .uri("lb://admin-service"))

                                // =========================
                                // USER SERVICE
                                // =========================
                                .route("user-service", r -> r
                                                .path("/api/auth/**", "/api/users/**", "/api/subscriptions/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .retry(config -> config
                                                                                .setRetries(3)
                                                                                .setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)))
                                                .uri("lb://user-service"))

                                // =========================
                                // PAYMENT SERVICE
                                // =========================
                                .route("payment-service", r -> r
                                                .path("/api/payments/**", "/api/packages/**", "/api/swish/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .retry(config -> config
                                                                                .setRetries(3)
                                                                                .setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)))
                                                .uri("lb://payment-service"))

                                // =========================
                                // QUIZ SERVICE
                                // =========================
                                .route("quiz-service", r -> r
                                                .path("/api/quizzes/**", "/api/quiz/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .retry(config -> config
                                                                                .setRetries(3)
                                                                                .setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)))
                                                .uri("lb://quiz-service"))

                                // =========================
                                // EXAM SERVICE
                                // =========================
                                .route("exam-service", r -> r
                                                .path("/api/exams/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .retry(config -> config
                                                                                .setRetries(3)
                                                                                .setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)))
                                                .uri("lb://exam-service"))

                                .build();
        }

        /**
         * Global filter för request logging
         * 
         * Loggar alla inkommande requests för monitoring och debugging
         */
        @Bean
        @Order(1)
        public GlobalFilter requestLoggingFilter() {
                return (exchange, chain) -> {
                        var request = exchange.getRequest();
                        log.info("🌐 [Gateway] {} {} from {}",
                                        request.getMethod(),
                                        request.getURI(),
                                        request.getRemoteAddress());

                        return chain.filter(exchange);
                };
        }

        /**
         * Global filter för response logging
         * 
         * Loggar response status codes för monitoring
         */
        @Bean
        @Order(2)
        public GlobalFilter responseLoggingFilter() {
                return (exchange, chain) -> {
                        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                                var response = exchange.getResponse();
                                var request = exchange.getRequest();
                                log.info("✅ [Gateway] {} {} → {}",
                                                request.getMethod(),
                                                request.getURI().getPath(),
                                                response.getStatusCode());
                        }));
                };
        }

        /**
         * Global filter för error handling
         * 
         * Fångar och loggar errors från downstream services
         */
        @Bean
        @Order(3)
        public GlobalFilter errorHandlingFilter() {
                return (exchange, chain) -> {
                        return chain.filter(exchange).onErrorResume(error -> {
                                log.error("❌ [Gateway] Error routing request to {}: {}",
                                                exchange.getRequest().getURI().getPath(),
                                                error.getMessage());
                                return Mono.error(error);
                        });
                };
        }
}
