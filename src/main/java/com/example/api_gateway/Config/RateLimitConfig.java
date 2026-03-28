package com.example.api_gateway.Config;

import java.util.concurrent.ConcurrentHashMap;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;

@Configuration
public class RateLimitConfig {

    // Cahce för att lagra buckets per IP-Address
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    /**
     * Hämtar eller skapar en Bucket för den angivna nyckeln (t.ex. IP-adress).
     *
     * Använder computeIfAbsent för att:
     * - returnera befintlig Bucket om den finns
     * - annars atomiskt skapa, lagra och returnera en ny Bucket
     *
     * Detta säkerställer att endast en Bucket skapas per key,
     * även vid samtidiga anrop (trådsäkerhet).
     *
     * @param key unik identifierare (t.ex. IP-adress)
     * @return befintlig eller ny skapad Bucket
     */
    public Bucket resolveBucket(String key) {
        return cache.computeIfAbsent(key, k -> createDefaultBucket());
    }

    /**
     * Hämta eller skapa en bucket för login (5 req/min)
     */
    public Bucket resolveLoginBucket(String key) {
        return cache.computeIfAbsent("Login: " + key, k -> createLoginBucket());
    }

    /**
     * Hämta eller skapa en bucket för registrering (2 req/min)
     */
    public Bucket resolveRegisterBucket(String key) {
        return cache.computeIfAbsent("Register: " + key, k -> createRegisterBucket());
    }

    /**
     * Hämta eller skapa en bucket för betalningar (10 req/min, per userId)
     */
    public Bucket resolvePaymentBucket(String key) {
        return cache.computeIfAbsent("Payment: " + key, k -> createPaymentBucket());
    }

    // ====================
    // BUCKET FACTORIES
    // ====================

    /**
     * Skapar en standard Bucket för generella requests (rate limiting).
     *
     * Syfte: Begränsa antalet requests för att undvika överbelastning och skydda
     * API:et.
     *
     * Bandwidth-regler:
     * - capacity(100) : Hinken rymmer max 100 tokens (max 100 requests innan
     * blockering).
     * - refillIntervally(100, Duration.ofMinutes(1)) : Fyller på 100 tokens varje
     * minut.
     *
     * Varje request konsumerar en token. När inga tokens finns kvar blockeras
     * ytterligare requests tills nästa refill sker.
     *
     * @return en ny Bucket med standardbegränsning
     */
    private Bucket createDefaultBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(100)
                .refillIntervally(100, Duration.ofMinutes(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Skapar en Bucket för login-försök (rate limiting).
     *
     * Syfte: Skydda mot brute force-attacker genom att begränsa antalet
     * login-försök.
     *
     * Bandwidth-regler:
     * - capacity(5) : Hinken rymmer max 5 tokens (max 5 requests innan blockering).
     * - refillIntervally(5, Duration.ofMinutes(1)) : Fyller på 5 tokens varje
     * minut.
     *
     * Varje login-request konsumerar en token. När inga tokens finns kvar blockeras
     * ytterligare requests tills nästa refill sker.
     *
     * @return en ny Bucket med login-begränsning
     */
    private Bucket createLoginBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(5)
                .refillIntervally(5, Duration.ofMinutes(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Skapar en Bucket för registreringsförsök (rate limiting).
     *
     * Syfte: Skydda mot spam och automatiserade registreringar genom att begränsa
     * antalet registreringsförsök per minut.
     *
     * Bandwidth-regler:
     * - capacity(2) : Hinken rymmer max 2 tokens (max 2 registreringsförsök innan
     * blockering).
     * - refillIntervally(2, Duration.ofMinutes(1)) : Fyller på 2 tokens varje
     * minut.
     *
     * Varje registreringsförsök konsumerar en token. När inga tokens finns kvar
     * blockeras
     * ytterligare requests tills nästa refill sker.
     *
     * @return en ny Bucket med register-begränsning
     */
    private Bucket createRegisterBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(2)
                .refillIntervally(2, Duration.ofMinutes(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Skapar en Bucket för betalningsförsök (rate limiting).
     *
     * Syfte: Skydda betalningsfunktioner mot överbelastning eller missbruk
     * genom att begränsa antalet betalningsförsök per timme.
     *
     * Bandwidth-regler:
     * - capacity(10) : Hinken rymmer max 10 tokens (max 10 betalningsförsök innan
     * blockering).
     * - refillIntervally(10, Duration.ofHours(1)) : Fyller på 10 tokens varje
     * timme.
     *
     * Varje betalningsförsök konsumerar en token. När inga tokens finns kvar
     * blockeras
     * ytterligare requests tills nästa refill sker.
     *
     * @return en ny Bucket med betalningsbegränsning
     */
    private Bucket createPaymentBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(10)
                .refillIntervally(10, Duration.ofHours(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

}
