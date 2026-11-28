package com.carbonx.mrv.infrastructure.client;

import com.carbonx.mrv.infrastructure.client.dto.IdentityCompanyResponse;
import com.carbonx.mrv.infrastructure.client.dto.PlateVerificationRequest;
import com.carbonx.mrv.infrastructure.client.dto.PlateVerificationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Declarative client for the identity-service internal channel. The URL is empty by default so
 * Eureka/Spring Cloud LoadBalancer resolves the instance; setting carbonx.clients.identity.base-url
 * points it at a fixed host for local runs.
 */
@FeignClient(name = "identity-service", url = "${carbonx.clients.identity.base-url:}",
        path = "/api/v1/internal")
public interface IdentityServiceFeignClient {

    @GetMapping("/companies/{companyId}")
    IdentityCompanyResponse getCompany(@PathVariable("companyId") Long companyId);

    @GetMapping("/companies/by-user/{userId}")
    IdentityCompanyResponse getCompanyByUserId(@PathVariable("userId") Long userId);

    @PostMapping("/vehicles/verify-plates")
    PlateVerificationResponse verifyPlates(@RequestBody PlateVerificationRequest request);
}
