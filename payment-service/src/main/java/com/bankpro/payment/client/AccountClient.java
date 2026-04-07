package com.bankpro.payment.client;

import com.bankpro.common.dto.TransferRequest;
import reactor.core.publisher.Mono;

public interface AccountClient {
    Mono<Void> debit(TransferRequest request);
    Mono<Void> credit(TransferRequest request);
}
