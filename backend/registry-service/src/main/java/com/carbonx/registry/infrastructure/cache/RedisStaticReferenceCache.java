package com.carbonx.registry.infrastructure.cache;

import com.carbonx.registry.application.port.out.StaticReferenceCachePort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Shared static reference cache of VM0038 project constants. Issuance writes the approved figures
 * here so later verification and payout calculations read them locally instead of calling
 * mrv-service on every request.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisStaticReferenceCache implements StaticReferenceCachePort {

    private static final String KEY_PREFIX = "registry:methodology:project:";
    private static final Duration TTL = Duration.ofHours(6);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<ProjectMethodologyRef> getProjectMethodology(Long projectId) {
        if (projectId == null) {
            return Optional.empty();
        }
        try {
            String json = redisTemplate.opsForValue().get(key(projectId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, ProjectMethodologyRef.class));
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("Methodology cache read failed for project {}: {}", projectId, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void putProjectMethodology(ProjectMethodologyRef ref) {
        if (ref == null || ref.projectId() == null) {
            return;
        }
        try {
            redisTemplate.opsForValue()
                    .set(key(ref.projectId()), objectMapper.writeValueAsString(ref), TTL);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("Methodology cache write failed for project {}: {}", ref.projectId(), ex.getMessage());
        }
    }

    private static String key(Long projectId) {
        return KEY_PREFIX + projectId;
    }
}
