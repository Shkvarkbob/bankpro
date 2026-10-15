package com.bankpro.payment.domain;

public enum PaymentStatus {
    CREATED,
    PROCESSING,
    COMPLETED,
    /** Terminal: nothing was (or remains) debited. A new idempotency key is needed to try again. */
    FAILED,
    /** The debit is being reversed. */
    COMPENSATING,
    /** The debit could not be reversed; retrying the same key resumes the reversal. Needs attention. */
    COMPENSATION_FAILED,
    /** Debit applied but the outcome of the credit is unknown; retrying the same key re-sends the credit. */
    CREDIT_UNCERTAIN
}
