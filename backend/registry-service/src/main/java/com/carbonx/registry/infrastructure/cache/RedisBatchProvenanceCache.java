package com.carbonx.registry.infrastructure.cache;

import com.carbonx.registry.application.port.out.BatchProvenanceCachePort;
import com.carbonx.registry.application.port.in.RegistryQueryUseCase.BatchProvenanceView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis read-through cache of batch provenance (30 min TTL). Every failure degrades to a database
 * read: availability of the registry must not depend on the cache tier.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisBatchProvenanceCache implements BatchProvenanceCachePort {

    private static final String KEY_PREFIX = "registry:batch:provenance:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<BatchProvenanceView> get(Long sourceReportId) {
        if (sourceReportId == null) {
            return Optional.empty();
        }
        try {
            String json = redisTemplate.opsForValue().get(key(sourceReportId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, BatchProvenanceView.class));
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("Provenance cache read failed for report {}: {}", sourceReportId, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(BatchProvenanceView view) {
        if (view == null || view.sourceReportId() == null) {
            return;
        }
        try {
            redisTemplate.opsForValue()
                    .set(key(view.sourceReportId()), objectMapper.writeValueAsString(view), TTL);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("Provenance cache write failed for report {}: {}", view.sourceReportId(), ex.getMessage());
        }
    }

    @Override
    public void evict(Long sourceReportId) {
        if (sourceReportId == null) {
            return;
        }
        try {
            redisTemplate.delete(key(sourceReportId));
        } catch (RuntimeException ex) {
            log.warn("Provenance cache eviction failed for report {}: {}", sourceReportId, ex.getMessage());
        }
    }

    private static String key(Long sourceReportId) {
        return KEY_PREFIX + sourceReportId;
    }
}
