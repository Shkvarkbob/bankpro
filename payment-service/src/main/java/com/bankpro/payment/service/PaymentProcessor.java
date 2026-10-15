package com.bankpro.payment.service;

import com.bankpro.common.dto.TransferRequest;
import com.bankpro.payment.client.AccountClient;
import com.bankpro.payment.domain.Payment;
import com.bankpro.payment.domain.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Orchestrates debit -> credit with idempotent retries and compensation.
 *
 * <p>Account calls end in one of three ways: success, a <i>definitive</i> rejection (4xx such as
 * insufficient funds or unknown account: nothing was applied) or an <i>ambiguous</i> failure
 * (timeout, connection error, 5xx, 409: the call may or may not have been applied).
 * <ul>
 *   <li>debit rejected: payment FAILED, nothing to undo;</li>
 *   <li>debit ambiguous: the debit is reversed (the reversal also blocks a late debit), payment FAILED;</li>
 *   <li>credit rejected: the debit is reversed, payment FAILED;</li>
 *   <li>credit ambiguous: the credit may have been applied, so the debit must NOT be reversed;
 *       payment CREDIT_UNCERTAIN and a retry with the same key re-sends the (idempotent) credit;</li>
 *   <li>reversal itself failed: payment COMPENSATION_FAILED, a retry with the same key resumes it.</li>
 * </ul>
 */
@Service
public class PaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    private final PaymentPersistenceService persistenceService;
    private final AccountClient accountClient;
    private final Duration processingTimeout;

    public PaymentProcessor(PaymentPersistenceService persistenceService,
                            AccountClient accountClient,
                            @Value("${payment.processing-timeout:60s}") Duration processingTimeout) {
        this.persistenceService = persistenceService;
        this.accountClient = accountClient;
        this.processingTimeout = processingTimeout;
    }

    public Mono<Payment> createAndProcess(TransferRequest request) {
        if (request.fromAccount().equals(request.toAccount())) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "fromAccount and toAccount must differ"));
        }
        return blocking(() -> persistenceService.findOrCreate(request))
                // Two concurrent requests with the same key: the loser re-reads the stored record.
                .onErrorResume(DataIntegrityViolationException.class, ex ->
                        blocking(() -> persistenceService.findByIdempotencyKey(request.idempotencyKey())))
                .flatMap(payment -> {
                    if (!matches(payment, request)) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Idempotency key was already used for a different payment"));
                    }
                    return dispatch(payment, request);
                });
    }

    private Mono<Payment> dispatch(Payment payment, TransferRequest request) {
        return switch (payment.getStatus()) {
            case COMPLETED, FAILED -> Mono.just(payment);
            case CREATED, CREDIT_UNCERTAIN ->
                    claim(payment, PaymentStatus.PROCESSING, Duration.ZERO).flatMap(p -> transfer(p, request));
            // Still PROCESSING: somebody else is working on it unless that worker looks dead.
            case PROCESSING ->
                    claim(payment, PaymentStatus.PROCESSING, processingTimeout).flatMap(p -> transfer(p, request));
            case COMPENSATION_FAILED -> resumeCompensation(payment, request, Duration.ZERO);
            case COMPENSATING -> resumeCompensation(payment, request, processingTimeout);
        };
    }

    private Mono<Payment> transfer(Payment payment, TransferRequest request) {
        Long id = payment.getId();
        return withRetry(accountClient.debit(request))
                .onErrorResume(ex -> isDefinitive(ex)
                        ? setStatus(id, PaymentStatus.FAILED).then(Mono.<Void>error(ex))
                        : compensate(id, request, ex))
                .then(Mono.defer(() -> withRetry(accountClient.credit(request))
                        .onErrorResume(ex -> isDefinitive(ex)
                                ? compensate(id, request, ex)
                                : creditUncertain(id, ex))))
                .then(Mono.defer(() -> setStatus(id, PaymentStatus.COMPLETED)));
    }

    private Mono<Payment> resumeCompensation(Payment payment, TransferRequest request, Duration minAge) {
        return claim(payment, PaymentStatus.COMPENSATING, minAge)
                .flatMap(p -> compensate(p.getId(), request,
                        new IllegalStateException("Payment failed, the debit has been reversed"))
                        .then(Mono.<Payment>empty()));
    }

    /** Reverses the debit; always ends in an error carrying {@code cause}. */
    private Mono<Void> compensate(Long id, TransferRequest request, Throwable cause) {
        return setStatus(id, PaymentStatus.COMPENSATING)
                .then(Mono.defer(() -> withRetry(accountClient.reverseDebit(request))))
                .then(Mono.defer(() -> setStatus(id, PaymentStatus.FAILED)))
                .then()
                .onErrorResume(compensationError -> {
                    log.error("Failed to reverse debit of payment {} ({}): manual intervention may be required",
                            request.idempotencyKey(), id, compensationError);
                    return setStatus(id, PaymentStatus.COMPENSATION_FAILED).then();
                })
                .then(Mono.<Void>error(cause));
    }

    private Mono<Void> creditUncertain(Long id, Throwable cause) {
        log.error("Outcome of credit for payment {} is unknown, debit is kept", id, cause);
        return setStatus(id, PaymentStatus.CREDIT_UNCERTAIN).then(Mono.<Void>error(cause));
    }

    private Mono<Payment> claim(Payment payment, PaymentStatus target, Duration minAge) {
        return blocking(() -> persistenceService.claim(payment.getId(), payment.getStatus(), target, minAge))
                .flatMap(claimed -> claimed.<Mono<Payment>>map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Payment is being processed, retry later"))));
    }

    private Mono<Payment> setStatus(Long id, PaymentStatus status) {
        return blocking(() -> persistenceService.updateStatus(id, status));
    }

    private static <T> Mono<T> blocking(Callable<T> call) {
        return Mono.fromCallable(call).subscribeOn(Schedulers.boundedElastic());
    }

    private static Mono<Void> withRetry(Mono<Void> call) {
        return call.retryWhen(Retry.backoff(3, Duration.ofMillis(200))
                .filter(ex -> !isDefinitive(ex))
                .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
    }

    /** A 4xx answer (other than timeout/conflict/throttling) proves the call had no effect. */
    static boolean isDefinitive(Throwable ex) {
        if (ex instanceof WebClientResponseException response) {
            HttpStatusCode status = response.getStatusCode();
            return status.is4xxClientError()
                    && status.value() != 408 && status.value() != 409 && status.value() != 429;
        }
        return false;
    }

    private static boolean matches(Payment payment, TransferRequest request) {
        return payment.getFromAccount().equals(request.fromAccount())
                && payment.getToAccount().equals(request.toAccount())
                && payment.getAmount().compareTo(request.amount()) == 0;
    }
}
