package com.carbonx.registry.application.port.out;

/** Outbound Port: PDF generation adapter for Carbon Issuance & Retirement Certificates. */
public interface CertificatePdfPort {
    byte[] renderRetirementCertificatePdf(CertificateRenderPayload payload);

    record CertificateRenderPayload(String certificateCode, String batchCode, String projectTitle, String beneficiaryName, long retiredQty, String serialRange, String reason) {}
}
