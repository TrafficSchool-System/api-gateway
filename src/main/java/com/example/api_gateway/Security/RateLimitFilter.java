package com.example.api_gateway.Security;

import com.example.api_gateway.Config.RateLimitConfig;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * RateLimitFilter
 *
 * Detta filter skyddar API:et från att få för många requests från samma klient.
 * Det använder Bucket4j för att implementera en token-bucket rate limiter.
 *
 * Filtret körs innan controllers och kan blockera requests
 * som överskrider definierade rate limits.
 */
@Component
@Order(1) // Kör detta filter först i filterkedjan (innan andra security-filter)
public class RateLimitFilter implements WebFilter {

    // Logger används för att skriva debug, info och error logs
    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);

    // Konfigurationen som innehåller alla buckets (login, register, payment,
    // default)
    private final RateLimitConfig rateLimitConfig;

    /**
     * Constructor injection.
     * Spring injicerar automatiskt RateLimitConfig-beanen här.
     */
    public RateLimitFilter(RateLimitConfig rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }

    /**
     * Denna metod körs för varje inkommande HTTP request.
     *
     * Här kontrolleras om klienten har överskridit sin rate limit.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        // Hämta requestens endpoint-path (t.ex /auth/login)
        String path = exchange.getRequest().getPath().toString();

        // Hämta HTTP-metoden (GET, POST, PUT etc.)
        String method = exchange.getRequest().getMethod().toString();

        // Hämta klientens IP-adress
        String clientIp = getClientIP(exchange);

        // Välj rätt rate-limit bucket beroende på endpoint
        Bucket bucket = selectBucket(path, method, clientIp, exchange);

        // Försök konsumera 1 token från bucket
        // Varje request kostar 1 token
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        // Kontrollera om token kunde konsumeras
        if (probe.isConsumed()) {

            // Token fanns → requesten är tillåten

            // Lägg till header i response som visar hur många requests som är kvar
            exchange.getResponse()
                    .getHeaders()
                    .add("X-Rate-Limit-Remaining",
                            String.valueOf(probe.getRemainingTokens()));

            // Logga att requesten godkändes
            logger.debug(
                    "Rate limit OK for {} {} from IP: {} (remaining: {})",
                    method,
                    path,
                    clientIp,
                    probe.getRemainingTokens());

            // Skicka requesten vidare till nästa filter eller controller
            return chain.filter(exchange);

        } else {

            // Token saknas → rate limit har överskridits

            // Beräkna hur många sekunder klienten måste vänta tills nästa token fylls på
            long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;

            // Logga att rate limit har överskridits
            logger.warn(
                    "Rate limit EXCEEDED for {} {} from IP: {} (retry after {} seconds)",
                    method,
                    path,
                    clientIp,
                    waitForRefill);

            // Sätt HTTP statuskod 429 (Too Many Requests)
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);

            // Lägg till header som talar om hur länge klienten måste vänta
            exchange.getResponse()
                    .getHeaders()
                    .add("X-Rate-Limit-Retry-After-Seconds",
                            String.valueOf(waitForRefill));

            // Avsluta requesten direkt (controller körs inte)
            return exchange.getResponse().setComplete();
        }
    }

    /**
     * Väljer rätt bucket beroende på endpoint och metod.
     *
     * Olika endpoints har olika rate limits.
     */
    private Bucket selectBucket(String path,
            String method,
            String clientIp,
            ServerWebExchange exchange) {

        // Login endpoint: skyddas hårdare för att stoppa brute force attacker
        if (path.contains("/api/auth/login") && method.equals("POST")) {

            logger.debug("Using LOGIN bucket for IP: {}", clientIp);

            return rateLimitConfig.resolveLoginBucket(clientIp);
        }

        // Register endpoint: begränsas för att förhindra bot-registrering
        // POST /api/users är register-endpointen
        if (path.equals("/api/users") && method.equals("POST")) {

            logger.debug("Using REGISTER bucket for IP: {}", clientIp);

            return rateLimitConfig.resolveRegisterBucket(clientIp);
        }

        // Payment endpoint: begränsas per userId istället för IP
        if (path.contains("/api/payments") && method.equals("POST")) {

            String userId = extractUserIdFromToken(exchange);

            if (userId != null) {

                logger.debug("Using PAYMENT bucket for userId: {}", userId);

                return rateLimitConfig.resolvePaymentBucket(userId);
            }
        }

        // Default rate limit för alla andra endpoints
        logger.debug("Using DEFAULT bucket for IP: {}", clientIp);

        return rateLimitConfig.resolveBucket(clientIp);
    }

    /**
     * Hämtar klientens riktiga IP-adress.
     *
     * Om applikationen körs bakom en proxy eller load balancer
     * används headern X-Forwarded-For.
     */
    private String getClientIP(ServerWebExchange exchange) {

        // Kontrollera först X-Forwarded-For headern
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");

        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {

            // Om flera IP finns separerade med komma
            // är första IP klientens riktiga IP
            return xForwardedFor.split(",")[0].trim();
        }

        // Fallback till remote address
        if (exchange.getRequest().getRemoteAddress() != null) {

            return exchange.getRequest()
                    .getRemoteAddress()
                    .getAddress()
                    .getHostAddress();
        }

        return "unknown";
    }

    /**
     * Extraherar userId från JWT token.
     *
     * Detta används för att rate-limita payment endpoints per användare.
     */
    private String extractUserIdFromToken(ServerWebExchange exchange) {

        try {

            // Hämta Authorization header
            String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

            // Kontrollera att headern innehåller en Bearer token
            if (authHeader != null && authHeader.startsWith("Bearer ")) {

                // Ta bort prefixet "Bearer "
                String token = authHeader.substring(7);

                // Här skulle man normalt parsa JWT och extrahera userId
                // För enkelhetens skull använder vi IP-adressen som fallback

                return getClientIP(exchange);
            }

        } catch (Exception e) {

            logger.error("Failed to extract userId from token", e);
        }

        // Om token saknas används IP-adressen istället
        return getClientIP(exchange);
    }
}