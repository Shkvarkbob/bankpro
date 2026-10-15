package com.bankpro.payment.client;

import com.bankpro.common.dto.TransferRequest;
import reactor.core.publisher.Mono;

public interface AccountClient {
    Mono<Void> debit(TransferRequest request);
    Mono<Void> credit(TransferRequest request);

    /** Undoes the debit made under the request's idempotency key (or cancels it if it never arrived). */
    Mono<Void> reverseDebit(TransferRequest request);
}
