package com.bankpro.payment.service;

import com.bankpro.common.dto.TransferRequest;
import com.bankpro.payment.client.AccountClient;
import com.bankpro.payment.domain.Payment;
import com.bankpro.payment.domain.PaymentStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;

@Service
public class PaymentProcessor {

    private final PaymentPersistenceService persistenceService;
    private final AccountClient accountClient;

    public PaymentProcessor(PaymentPersistenceService persistenceService, AccountClient accountClient) {
        this.persistenceService = persistenceService;
        this.accountClient = accountClient;
    }

    public Mono<Payment> createAndProcess(TransferRequest request) {
        return Mono.fromCallable(() -> persistenceService.findOrCreate(request))
                .subscribeOn(Schedulers.boundedElastic())
                // Два одновременных запроса с одним ключом: второй получает DataIntegrityViolationException,
                // перечитываем уже сохранённую запись вместо падения с ошибкой.
                .onErrorResume(DataIntegrityViolationException.class, ex ->
                        Mono.fromCallable(() -> persistenceService.findByIdempotencyKey(request.idempotencyKey()))
                                .subscribeOn(Schedulers.boundedElastic())
                )
                .flatMap(payment -> {
                    if (payment.getStatus() == PaymentStatus.COMPLETED) {
                        return Mono.just(payment);
                    }
                    return executeTransfer(payment, request);
                });
    }

    private Mono<Payment> executeTransfer(Payment payment, TransferRequest request) {
        return Mono.fromCallable(() -> persistenceService.markProcessing(payment))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(p ->
                        accountClient.debit(request)
                                .retryWhen(Retry.backoff(3, Duration.ofMillis(200)))
                                .then(
                                        accountClient.credit(request)
                                                .retryWhen(Retry.backoff(3, Duration.ofMillis(200)))
                                                // Кредит упал после успешного дебета — компенсируем списание.
                                                .onErrorResume(ex -> compensateDebit(request).then(Mono.error(ex)))
                                )
                                .then(Mono.fromCallable(() -> persistenceService.markCompleted(p))
                                        .subscribeOn(Schedulers.boundedElastic()))
                                .onErrorResume(ex ->
                                        Mono.fromCallable(() -> persistenceService.markFailed(p))
                                                .subscribeOn(Schedulers.boundedElastic())
                                                .then(Mono.error(ex))
                                )
                );
    }

    private Mono<Void> compensateDebit(TransferRequest original) {
        TransferRequest compensation = new TransferRequest(
                original.toAccount(),
                original.fromAccount(),
                original.amount(),
                "COMP-" + original.idempotencyKey()
        );
        return accountClient.credit(compensation)
                .retryWhen(Retry.backoff(3, Duration.ofMillis(200)))
                .onErrorResume(ex -> Mono.empty());
    }
}
