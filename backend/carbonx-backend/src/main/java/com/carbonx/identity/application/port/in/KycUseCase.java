package com.carbonx.identity.application.port.in;

import java.util.List;

/** Inbound Use-Case Port: EV Owner & Company KYC verification. */
public interface KycUseCase {
    KycProfileView submitEvOwnerKyc(Long evOwnerId, SubmitEvKycCommand command);

    CompanyKycView submitCompanyKyc(Long userId, SubmitCompanyKycCommand command);

    KycProfileView getMyEvKyc(Long evOwnerId);

    CompanyKycView getMyCompanyKyc(Long userId);

    List<CompanyKycView> listApprovedCompanies();

    void reviewCompanyKyc(Long adminUserId, Long companyId, boolean approved, String note);

    record SubmitEvKycCommand(String documentType, String documentNumber, String phone) {}

    record SubmitCompanyKycCommand(String businessName, String taxCode, String address) {}

    record KycProfileView(Long id, Long evOwnerId, String documentType, String documentNumber, String phone, String status) {}

    record CompanyKycView(Long companyId, Long userId, String businessName, String taxCode, String address, String status) {}
}
