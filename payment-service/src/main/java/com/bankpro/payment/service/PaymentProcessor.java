package com.bankpro.payment.service;

import com.bankpro.common.dto.TransferRequest;
import com.bankpro.payment.client.AccountClient;
import com.bankpro.payment.domain.Payment;
import com.bankpro.payment.domain.PaymentStatus;
import com.bankpro.payment.repository.PaymentRepository;
import lombok.var;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.OffsetDateTime;

@Service
public class PaymentProcessor {

    private final PaymentRepository paymentRepository;
    private final AccountClient accountClient;

    public PaymentProcessor(PaymentRepository paymentRepository, AccountClient accountClient) {
        this.paymentRepository = paymentRepository;
        this.accountClient = accountClient;
    }

    @Transactional
    public Mono<Payment> createAndProcess(TransferRequest request) {
        return Mono.fromSupplier(() -> paymentRepository.findByIdempotencyKey(request.idempotencyKey()).orElseGet(() -> {
            Payment p = new Payment();
            p.setFromAccount(request.fromAccount());
            p.setToAccount(request.toAccount());
            p.setAmount(request.amount());
            p.setIdempotencyKey(request.idempotencyKey());
            p.setStatus(PaymentStatus.PROCESSING);
            p.setCreatedAt(OffsetDateTime.now());
            return paymentRepository.save(p);
        })).flatMap(payment ->
                accountClient.debit(request).retryWhen(Retry.backoff(3, Duration.ofMillis(200)))
                        .then(accountClient.credit(request).retryWhen(Retry.backoff(3, Duration.ofMillis(200))))
                        .then(Mono.fromSupplier(() -> {
                            payment.setStatus(PaymentStatus.COMPLETED);
                            return paymentRepository.save(payment);
                        }
                        ))
                        .onErrorResume(ex -> Mono.fromSupplier(() -> {
                            payment.setStatus(PaymentStatus.FAILED);
                            return paymentRepository.save(payment);
                        }
                        )
                                .then(Mono.error(ex)))
        );
    }
}
