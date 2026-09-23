package br.com.nexapay.payment.domain;

public enum PaymentStatus {
    SCHEDULED,
    PENDING,
    REVIEW,
    COMPLETED,
    REJECTED,
    CANCELLED
}
