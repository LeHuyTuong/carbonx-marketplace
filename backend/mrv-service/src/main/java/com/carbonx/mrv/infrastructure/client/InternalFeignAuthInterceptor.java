package com.carbonx.mrv.infrastructure.client;

import com.carbonx.common.security.InternalServiceAuth;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Attaches the service-to-service token and trace id to outbound calls targeting identity-service. */
@Component
public class InternalFeignAuthInterceptor implements RequestInterceptor {

    private final String internalToken;

    public InternalFeignAuthInterceptor(@Value("${carbonx.security.internal-token:}") String internalToken) {
        this.internalToken = internalToken;
    }

    @Override
    public void apply(RequestTemplate template) {
        if (internalToken != null && !internalToken.isBlank()) {
            template.header(InternalServiceAuth.HEADER_NAME, internalToken);
        }
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            template.header("X-Request-Trace", traceId);
        }
    }
}
