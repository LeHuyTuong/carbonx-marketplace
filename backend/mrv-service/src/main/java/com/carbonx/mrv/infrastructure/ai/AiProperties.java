package com.carbonx.mrv.infrastructure.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Timeout and fallback metadata of the Spring AI adapter. */
@ConfigurationProperties(prefix = "carbonx.ai")
public record AiProperties(long timeoutSeconds, String fallbackVersion) {

    public static final String DEFAULT_FALLBACK_VERSION = "rules-dq1-dq8-v1";

    public AiProperties {
        timeoutSeconds = timeoutSeconds <= 0 ? 15L : timeoutSeconds;
        fallbackVersion = fallbackVersion == null || fallbackVersion.isBlank()
                ? DEFAULT_FALLBACK_VERSION
                : fallbackVersion.trim();
    }
}
