package com.carbonx.identity.infrastructure.persistence.repository;

import com.carbonx.identity.infrastructure.persistence.entity.CompanyJpaEntity;
import com.carbonx.identity.infrastructure.persistence.entity.KycProfileJpaEntity;
import com.carbonx.identity.infrastructure.persistence.entity.UserJpaEntity;
import com.carbonx.identity.infrastructure.persistence.entity.VehicleJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:identity_jpa;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class IdentityJpaRepositoriesTest {

    @Autowired
    private UserJpaRepository userRepository;

    @Autowired
    private CompanyJpaRepository companyRepository;

    @Autowired
    private KycProfileJpaRepository kycRepository;

    @Autowired
    private VehicleJpaRepository vehicleRepository;

    @Test
    @DisplayName("Derived JPA queries for users, companies, KYC and vehicles execute cleanly")
    void derivedQueriesExecuteCleanly() {
        UserJpaEntity admin = userRepository.save(UserJpaEntity.builder()
                .email("admin@carbonx.vn")
                .passwordHash("hash")
                .fullName("Admin")
                .role("ROLE_ADMIN")
                .status("ACTIVE")
                .build());
        UserJpaEntity cva = userRepository.save(UserJpaEntity.builder()
                .email("cva@carbonx.vn")
                .passwordHash("hash")
                .fullName("CVA")
                .role("ROLE_CVA")
                .status("ACTIVE")
                .build());

        CompanyJpaEntity company = companyRepository.save(CompanyJpaEntity.builder()
                .userId(admin.getId())
                .businessName("Carbon Fleet JSC")
                .taxCode("0312345678")
                .businessLicense("GP-001")
                .address("HCM")
                .kycStatus("APPROVED")
                .build());

        kycRepository.save(KycProfileJpaEntity.builder()
                .evOwnerId(cva.getId())
                .fullName("Owner A")
                .documentType("CCCD")
                .documentNumber("079090000111")
                .phone("0909123456")
                .country("VN")
                .address("Q1")
                .birthDate("1995-01-01")
                .gender("MALE")
                .kycStatus("PENDING")
                .build());

        vehicleRepository.save(VehicleJpaEntity.builder()
                .evOwnerId(cva.getId())
                .companyId(company.getId())
                .plateNumber("51A12345")
                .brand("VinFast")
                .model("VF8")
                .status("ACTIVE")
                .build());

        assertThat(userRepository.findAllByOrderByIdDesc()).hasSize(2);
        assertThat(userRepository.findByRoleOrderByIdDesc("ROLE_CVA")).hasSize(1);
        assertThat(companyRepository.findAllByOrderByIdDesc()).hasSize(1);
        assertThat(kycRepository.findAllByOrderByIdDesc()).hasSize(1);
        assertThat(vehicleRepository.findByCompanyIdOrderByIdDesc(company.getId())).hasSize(1);
        assertThat(vehicleRepository.findByCompanyIdAndPlateNumberIn(company.getId(), List.of("51A12345"))).hasSize(1);
    }
}
