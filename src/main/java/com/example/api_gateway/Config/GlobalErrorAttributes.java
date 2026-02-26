package com.example.api_gateway.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.util.Map;

/**
 * ==========================================
 * GLOBAL ERROR HANDLER
 * ==========================================
 * Centraliserad error handling för API Gateway.
 * 
 * Hanterar:
 * - Service timeout errors
 * - Connection errors
 * - 404 Not Found (service inte tillgänglig)
 * - 500 Internal Server Error från downstream services
 * - Load balancer errors
 * 
 * Returnerar strukturerade error responses till frontend.
 */
@Component
public class GlobalErrorAttributes extends DefaultErrorAttributes {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorAttributes.class);

    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        Map<String, Object> errorAttributes = super.getErrorAttributes(request, options);

        // Hämta error från request
        Throwable error = getError(request);

        // Logga error för monitoring
        log.error("❌ [Gateway Error] {} - {}",
                errorAttributes.get("path"),
                error.getMessage());

        // Anpassa error message baserat på error-typ
        if (error.getMessage().contains("Connection refused")) {
            errorAttributes.put("message", "Service temporarily unavailable. Please try again later.");
            errorAttributes.put("error", "Service Unavailable");
        } else if (error.getMessage().contains("Timeout")) {
            errorAttributes.put("message", "Request timeout. The service took too long to respond.");
            errorAttributes.put("error", "Gateway Timeout");
        } else if (error.getMessage().contains("No available")) {
            errorAttributes.put("message", "Service not found. Please check if the service is running.");
            errorAttributes.put("error", "Service Not Found");
        }

        // Ta bort känslig information från error response
        errorAttributes.remove("trace");

        return errorAttributes;
    }
}
