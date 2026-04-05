package com.example.api_gateway.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * ==========================================
 * RAILWAY-SPECIFIC GATEWAY CONFIGURATION
 * ==========================================
 * 
 * Railway deployment använder DIREKT service-to-service kommunikation
 * via Railway's privata nätverk (.railway.internal) istället för Eureka.
 * 
 * IMPORTANT: This config is ONLY active when SPRING_PROFILES_ACTIVE=railway
 * 
 * Services använder Railway's interna DNS:
 * - userservice.railway.internal:8081
 * - adminservice.railway.internal:8084
 * - paymentservice.railway.internal:8085
 * - quizservice.railway.internal:8082
 * - examservice.railway.internal:8083
 */
@Configuration
@Profile("railway")
public class RailwayGatewayConfig {

        private static final Logger log = LoggerFactory.getLogger(RailwayGatewayConfig.class);

        @Value("${services.user-service.url:http://userservice.railway.internal:8081}")
        private String userServiceUrl;

        @Value("${services.admin-service.url:http://adminservice.railway.internal:8084}")
        private String adminServiceUrl;

        @Value("${services.payment-service.url:http://paymentservice.railway.internal:8085}")
        private String paymentServiceUrl;

        @Value("${services.quiz-service.url:http://quizservice.railway.internal:8082}")
        private String quizServiceUrl;

        @Value("${services.exam-service.url:http://examservice.railway.internal:8083}")
        private String examServiceUrl;

        /**
         * Railway Route Configuration med direkta service URLs
         * 
         * VIKTIGT: Använder INTE Eureka load balancing (lb://)
         * Använder istället direkta HTTP URLs till Railway's privata nätverk
         */
        @Bean
        public RouteLocator railwayRouteLocator(RouteLocatorBuilder builder) {
                log.info("🚂 [Railway Gateway Config] Initializing with direct service URLs:");
                log.info("  - User Service: {}", userServiceUrl);
                log.info("  - Admin Service: {}", adminServiceUrl);
                log.info("  - Payment Service: {}", paymentServiceUrl);
                log.info("  - Quiz Service: {}", quizServiceUrl);
                log.info("  - Exam Service: {}", examServiceUrl);

                return builder.routes()
                                // =========================
                                // ADMIN ROUTES - MER SPECIFIKA FÖRST!
                                // =========================

                                // Admin User Statistics (UserService)
                                .route("admin-user-statistics", r -> r
                                                .path("/api/admin/users/statistics")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .retry(config -> config
                                                                                .setRetries(3)
                                                                                .setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)))
                                                .uri(userServiceUrl))

                                // Admin User DELETE (UserService)
                                .route("admin-user-delete", r -> r
                                                .method(org.springframework.http.HttpMethod.DELETE)
                                                .and()
                                                .path("/api/admin/users/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .retry(config -> config
                                                                                .setRetries(3)
                                                                                .setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)))
                                                .uri(userServiceUrl))

                                // Admin User UPDATE (UserService)
                                .route("admin-user-update", r -> r
                                                .method(org.springframework.http.HttpMethod.PUT)
                                                .and()
                                                .path("/api/admin/users/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .retry(config -> config
                                                                                .setRetries(3)
                                                                                .setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)))
                                                .uri(userServiceUrl))

                                // Admin User Management READ (AdminService - aggregerad data)
                                .route("admin-users", r -> r
                                                .path("/api/admin/users/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .retry(config -> config
                                                                                .setRetries(3)
                                                                                .setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)))
                                                .uri(adminServiceUrl))

                                // Admin Exam Management (ExamService)
                                .route("admin-exams", r -> r
                                                .path("/api/admin/exams/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .retry(config -> config
                                                                                .setRetries(3)
                                                                                .setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)))
                                                .uri(examServiceUrl))

                                // Admin Quiz Management (QuizService)
                                .route("admin-quizzes", r -> r
                                                .path("/api/admin/quizzes/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .retry(config -> config
                                                                                .setRetries(3)
                                                                                .setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)))
                                                .uri(quizServiceUrl))

                                // Admin Payment Management (PaymentService)
                                .route("admin-payments", r -> r
                                                .path("/api/admin/payments/**", "/api/admin/packages/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .retry(config -> config
                                                                                .setRetries(3)
                                                                                .setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)))
                                                .uri(paymentServiceUrl))

                                // Admin Aggregation Service (AdminService) - SIST!
                                .route("admin-service", r -> r
                                                .path("/api/admin/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .retry(config -> config
                                                                                .setRetries(3)
                                                                                .setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)))
                                                .uri(adminServiceUrl))

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
                                                .uri(userServiceUrl))

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
                                                .uri(paymentServiceUrl))

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
                                                .uri(quizServiceUrl))

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
                                                .uri(examServiceUrl))

                                .build();
        }
}
