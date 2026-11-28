package com.carbonx.mrv.infrastructure.client;

import com.carbonx.mrv.application.exception.MrvApplicationException;
import com.carbonx.mrv.application.exception.MrvErrorCode;
import com.carbonx.mrv.application.port.out.IdentityServiceClientPort;
import com.carbonx.mrv.infrastructure.client.dto.IdentityCompanyResponse;
import com.carbonx.mrv.infrastructure.client.dto.PlateVerificationRequest;
import com.carbonx.mrv.infrastructure.client.dto.PlateVerificationResponse;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Guards every identity-service call with a Resilience4j circuit breaker and retry. Application
 * exceptions (404, not-approved company) are registered as {@code ignoreExceptions} in
 * application.yml so a misbehaving caller cannot open the circuit; transport failures fall back to
 * a 503 rather than returning fabricated data.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdentityServiceClientAdapter implements IdentityServiceClientPort {

    private static final String APPROVED = "APPROVED";

    private final IdentityServiceFeignClient client;

    @Override
    @CircuitBreaker(name = "identityService", fallbackMethod = "companyNameFallback")
    @Retry(name = "identityService")
    public String fetchApprovedCompanyName(Long companyId) {
        if (companyId == null) {
            throw MrvApplicationException.badRequest("companyId is required");
        }
        IdentityCompanyResponse company;
        try {
            company = client.getCompany(companyId);
        } catch (FeignException.NotFound ex) {
            throw MrvApplicationException.notFound("Company not found in identity-service: " + companyId);
        }
        if (company == null) {
            throw MrvApplicationException.notFound("Company not found in identity-service: " + companyId);
        }
        if (!APPROVED.equalsIgnoreCase(company.kycStatus())) {
            throw new MrvApplicationException(MrvErrorCode.COMPANY_NOT_APPROVED,
                    "Company " + companyId + " is not KYC-approved (status " + company.kycStatus() + ")");
        }
        return company.businessName() == null || company.businessName().isBlank()
                ? "Company #" + companyId
                : company.businessName();
    }

    /** Circuit-open / transport failure: the caller cannot safely snapshot a company name. */
    public String companyNameFallback(Long companyId, Throwable failure) {
        log.warn("identity-service company lookup failed companyId={}: {}", companyId, failure.getMessage());
        throw MrvApplicationException.unavailable("identity-service", failure);
    }

    @Override
    @CircuitBreaker(name = "identityService", fallbackMethod = "resolveCompanyIdFallback")
    @Retry(name = "identityService")
    public Long resolveCompanyIdByUserId(Long userId) {
        if (userId == null) {
            throw MrvApplicationException.badRequest("userId is required");
        }
        IdentityCompanyResponse company;
        try {
            company = client.getCompanyByUserId(userId);
        } catch (FeignException.NotFound ex) {
            throw MrvApplicationException.notFound("Company not found for user in identity-service: " + userId);
        }
        if (company == null || company.companyId() == null) {
            throw MrvApplicationException.notFound("Company not found for user in identity-service: " + userId);
        }
        return company.companyId();
    }

    public Long resolveCompanyIdFallback(Long userId, Throwable failure) {
        log.warn("identity-service user->company lookup failed userId={}: {}", userId, failure.getMessage());
        throw MrvApplicationException.unavailable("identity-service", failure);
    }

    @Override
    @CircuitBreaker(name = "identityService", fallbackMethod = "verifyPlatesFallback")
    @Retry(name = "identityService")
    public List<String> verifyRegisteredPlates(Long companyId, List<String> plateNumbers) {
        if (companyId == null || plateNumbers == null || plateNumbers.isEmpty()) {
            return List.of();
        }
        PlateVerificationResponse response;
        try {
            response = client.verifyPlates(new PlateVerificationRequest(companyId, plateNumbers));
        } catch (FeignException.NotFound ex) {
            throw MrvApplicationException.notFound("Company not found in identity-service: " + companyId);
        }
        if (response == null || response.verifiedPlates() == null) {
            return List.of();
        }
        return List.copyOf(response.verifiedPlates());
    }

    /**
     * Circuit-open / transport failure. The caller decides how to degrade: the upload path skips
     * the cross-check and logs it, because an identity outage must not block MRV submissions.
     */
    public List<String> verifyPlatesFallback(Long companyId, List<String> plateNumbers, Throwable failure) {
        log.warn("identity-service plate verification failed companyId={} plates={}: {}",
                companyId, plateNumbers == null ? 0 : plateNumbers.size(), failure.getMessage());
        throw MrvApplicationException.unavailable("identity-service", failure);
    }
}
