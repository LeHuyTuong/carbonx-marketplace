package com.carbonx.trading.infrastructure.client;

import com.carbonx.common.security.InternalServiceAuth;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Propagates the caller's bearer token on user-scoped registry reads, attaches the fail-closed
 * {@code X-Internal-Service-Token} on {@code /api/v1/internal/**} calls, and carries trace id.
 */
@Component
public class FeignAuthForwardingInterceptor implements RequestInterceptor {

    static final String TRACE_HEADER = "X-Request-Trace";
    private static final String INTERNAL_PATH_PREFIX = "/api/v1/internal/";

    private final String internalToken;

    public FeignAuthForwardingInterceptor(@Value("${carbonx.security.internal-token:}") String internalToken) {
        this.internalToken = internalToken;
    }

    @Override
    public void apply(RequestTemplate template) {
        if (isInternalRequest(template) && internalToken != null && !internalToken.isBlank()) {
            template.header(InternalServiceAuth.HEADER_NAME, internalToken.trim());
        }
        HttpServletRequest request = currentRequest();
        if (request == null) {
            addTrace(template, MDC.get("traceId"));
            return;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && !authorization.isBlank()) {
            template.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        String traceId = request.getHeader(TRACE_HEADER);
        addTrace(template, traceId == null || traceId.isBlank() ? MDC.get("traceId") : traceId);
    }

    private static boolean isInternalRequest(RequestTemplate template) {
        String url = template.url();
        String path = template.path();
        return (url != null && url.contains(INTERNAL_PATH_PREFIX))
                || (path != null && path.contains(INTERNAL_PATH_PREFIX));
    }

    private static void addTrace(RequestTemplate template, String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            template.header(TRACE_HEADER, traceId);
        }
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
