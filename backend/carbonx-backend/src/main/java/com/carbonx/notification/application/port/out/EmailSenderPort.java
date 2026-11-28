package com.carbonx.notification.application.port.out;

/** Outbound Port: Transactional Email delivery (OTP, Approval, Payout receipt). */
public interface EmailSenderPort {
    void send(String toEmail, String subject, String htmlBody);
}
