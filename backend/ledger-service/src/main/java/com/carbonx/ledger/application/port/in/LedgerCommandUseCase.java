package com.carbonx.ledger.application.port.in;

import com.carbonx.common.domain.Money;

public interface LedgerCommandUseCase {
    void settleOrderPayment(String idempotencyKey, Long orderRef, Long buyerUserId, Long sellerUserId, Money gross, Money fee);
    void compensateOrderPayment(String idempotencyKey, Long orderRef, String reason);
    String initiateDeposit(Long userId, Money amount, String provider);
    void handleGatewayWebhook(String provider, String payload, String signature);
    void savePaymentDetails(Long userId, String bankName, String accountNumber, String accountHolder, String branch);
    Long requestWithdrawal(Long userId, Long bankAccountId, Money amount);
    void processWithdrawal(Long adminId, Long withdrawalId, boolean approved);
    Long executeProfitDistribution(Long companyId, Long sourceReportId, String idempotencyKey);
}
