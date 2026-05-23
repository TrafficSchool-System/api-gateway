package com.example.api_gateway.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

import reactor.core.publisher.Mono;

/**
 * ==========================================
 * API GATEWAY CONFIGURATION (LOCAL/EUREKA)
 * ==========================================
 * Centraliserad routing konfiguration för alla microservices.
 * 
 * VIKTIGT: Denna config är ENDAST aktiv för lokal utveckling (default profil)
 * För Produktion deployment, använd ProductionGatewayConfig istället!
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
@Profile("local") 
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
                                // ADMIN ROUTES - MER SPECIFIKA FÖRST!
                                // =========================

                                // Admin User Statistics (UserService - enkla räkningar)
                                .route("admin-user-statistics", r -> r
                                                .path("/api/admin/users/statistics")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .retry(config -> config
                                                                                .setRetries(3)
                                                                                .setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)))
                                                .uri("lb://user-service"))

                                // Admin User DELETE (UserService - direct access for CRUD)
                                .route("admin-user-delete", r -> r
                                                .method(org.springframework.http.HttpMethod.DELETE)
                                                .and()
                                                .path("/api/admin/users/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .retry(config -> config
                                                                                .setRetries(3)
                                                                                .setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)))
                                                .uri("lb://user-service"))

                                // Admin User UPDATE (UserService - direct access for CRUD)
                                .route("admin-user-update", r -> r
                                                .method(org.springframework.http.HttpMethod.PUT)
                                                .and()
                                                .path("/api/admin/users/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .retry(config -> config
                                                                                .setRetries(3)
                                                                                .setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)))
                                                .uri("lb://user-service"))

                                // Admin User Management READ (AdminService - aggregerad data)
                                .route("admin-users", r -> r
                                                .path("/api/admin/users/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .retry(config -> config
                                                                                .setRetries(3)
                                                                                .setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)))
                                                .uri("lb://admin-service"))

                                // Admin Exam Management (ExamService)
                                .route("admin-exams", r -> r
                                                .path("/api/admin/exams/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .retry(config -> config
                                                                                .setRetries(3)
                                                                                .setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)))
                                                .uri("lb://exam-service"))

                                // Admin Quiz Management (QuizService)
                                .route("admin-quizzes", r -> r
                                                .path("/api/admin/quizzes/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .retry(config -> config
                                                                                .setRetries(3)
                                                                                .setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)))
                                                .uri("lb://quiz-service"))

                                // Admin Payment Management (PaymentService)
                                .route("admin-payments", r -> r
                                                .path("/api/admin/payments/**", "/api/admin/packages/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .retry(config -> config
                                                                                .setRetries(3)
                                                                                .setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)))
                                                .uri("lb://payment-service"))

                                // Admin Aggregation Service (AdminService) - SIST!
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
                                                .path("/api/payments/**", "/api/packages/**", "/api/swish/**",
                                                                "/api/webhooks/**")
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
                        log.debug("[Gateway] {} {} from {}",
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
                                log.debug("[Gateway] {} {} -> {}",
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
