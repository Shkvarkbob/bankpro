package com.bankpro.payment.service;

import com.bankpro.common.dto.TransferRequest;
import com.bankpro.payment.client.AccountClient;
import com.bankpro.payment.domain.Payment;
import com.bankpro.payment.domain.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PaymentProcessorTest {

    private static final TransferRequest REQUEST =
            new TransferRequest("A", "B", new BigDecimal("10.00"), "key-1");

    private PaymentPersistenceService persistence;
    private AccountClient accounts;
    private PaymentProcessor processor;
    private Payment payment;

    @BeforeEach
    void setUp() {
        persistence = mock(PaymentPersistenceService.class);
        accounts = mock(AccountClient.class);
        processor = new PaymentProcessor(persistence, accounts, Duration.ofSeconds(60));

        payment = payment(PaymentStatus.CREATED);
        when(persistence.findOrCreate(any())).thenReturn(payment);
        when(persistence.claim(eq(1L), any(), any(), any())).thenReturn(Optional.of(payment));
        when(persistence.updateStatus(eq(1L), any())).thenReturn(payment);
        when(accounts.debit(any())).thenReturn(Mono.empty());
        when(accounts.credit(any())).thenReturn(Mono.empty());
        when(accounts.reverseDebit(any())).thenReturn(Mono.empty());
    }

    @Test
    void successfulTransferCompletes() {
        StepVerifier.create(processor.createAndProcess(REQUEST)).expectNextCount(1).verifyComplete();

        verify(persistence).updateStatus(1L, PaymentStatus.COMPLETED);
        verify(accounts, never()).reverseDebit(any());
    }

    @Test
    void sameAccountIsRejected() {
        TransferRequest same = new TransferRequest("A", "A", BigDecimal.ONE, "key-2");

        StepVerifier.create(processor.createAndProcess(same))
                .expectErrorSatisfies(e -> assertInstanceOf(ResponseStatusException.class, e))
                .verify();
        verifyNoInteractions(persistence, accounts);
    }

    @Test
    void completedPaymentIsReturnedWithoutCallingAccounts() {
        when(persistence.findOrCreate(any())).thenReturn(payment(PaymentStatus.COMPLETED));

        StepVerifier.create(processor.createAndProcess(REQUEST)).expectNextCount(1).verifyComplete();

        verifyNoInteractions(accounts);
    }

    @Test
    void keyReusedForDifferentPaymentIsConflict() {
        TransferRequest other = new TransferRequest("A", "B", new BigDecimal("99.00"), "key-1");

        StepVerifier.create(processor.createAndProcess(other))
                .expectErrorSatisfies(e -> assertEquals(409, ((ResponseStatusException) e).getStatusCode().value()))
                .verify();
        verifyNoInteractions(accounts);
    }

    @Test
    void insufficientFundsFailsWithoutRetryOrCompensation() {
        when(accounts.debit(any())).thenReturn(Mono.error(http(422)));

        StepVerifier.create(processor.createAndProcess(REQUEST))
                .expectErrorSatisfies(e -> assertInstanceOf(WebClientResponseException.class, e))
                .verify();

        verify(accounts, times(1)).debit(any());
        verify(accounts, never()).credit(any());
        verify(accounts, never()).reverseDebit(any());
        verify(persistence).updateStatus(1L, PaymentStatus.FAILED);
    }

    @Test
    void transientDebitErrorIsRetried() {
        AtomicInteger calls = new AtomicInteger();
        when(accounts.debit(any())).thenReturn(Mono.defer(() ->
                calls.incrementAndGet() < 3 ? Mono.error(http(409)) : Mono.empty()));

        StepVerifier.create(processor.createAndProcess(REQUEST)).expectNextCount(1).verifyComplete();

        assertEquals(3, calls.get());
        verify(persistence).updateStatus(1L, PaymentStatus.COMPLETED);
    }

    @Test
    void ambiguousDebitIsReversed() {
        when(accounts.debit(any())).thenReturn(Mono.error(new TimeoutException("timeout")));

        StepVerifier.create(processor.createAndProcess(REQUEST))
                .expectErrorSatisfies(e -> assertInstanceOf(TimeoutException.class, e))
                .verify();

        verify(accounts).reverseDebit(REQUEST);
        verify(accounts, never()).credit(any());
        verify(persistence).updateStatus(1L, PaymentStatus.FAILED);
    }

    @Test
    void rejectedCreditReversesTheDebit() {
        when(accounts.credit(any())).thenReturn(Mono.error(http(404)));

        StepVerifier.create(processor.createAndProcess(REQUEST))
                .expectErrorSatisfies(e -> assertInstanceOf(WebClientResponseException.class, e))
                .verify();

        verify(accounts).reverseDebit(REQUEST);
        verify(persistence).updateStatus(1L, PaymentStatus.FAILED);
    }

    @Test
    void ambiguousCreditKeepsTheDebit() {
        when(accounts.credit(any())).thenReturn(Mono.error(new TimeoutException("timeout")));

        StepVerifier.create(processor.createAndProcess(REQUEST))
                .expectErrorSatisfies(e -> assertInstanceOf(TimeoutException.class, e))
                .verify();

        verify(accounts, never()).reverseDebit(any());
        verify(persistence).updateStatus(1L, PaymentStatus.CREDIT_UNCERTAIN);
    }

    @Test
    void failedReversalIsRecorded() {
        when(accounts.credit(any())).thenReturn(Mono.error(http(404)));
        when(accounts.reverseDebit(any())).thenReturn(Mono.error(http(500)));

        StepVerifier.create(processor.createAndProcess(REQUEST))
                .expectErrorSatisfies(e -> assertEquals(404, ((WebClientResponseException) e).getStatusCode().value()))
                .verify();

        verify(persistence).updateStatus(1L, PaymentStatus.COMPENSATION_FAILED);
        verify(persistence, never()).updateStatus(1L, PaymentStatus.FAILED);
    }

    @Test
    void compensationFailedPaymentResumesOnlyTheReversal() {
        when(persistence.findOrCreate(any())).thenReturn(payment(PaymentStatus.COMPENSATION_FAILED));

        StepVerifier.create(processor.createAndProcess(REQUEST))
                .expectErrorSatisfies(e -> assertInstanceOf(IllegalStateException.class, e))
                .verify();

        verify(accounts).reverseDebit(REQUEST);
        verify(accounts, never()).debit(any());
        verify(accounts, never()).credit(any());
        verify(persistence).updateStatus(1L, PaymentStatus.FAILED);
    }

    @Test
    void paymentInProgressElsewhereIsConflict() {
        when(persistence.findOrCreate(any())).thenReturn(payment(PaymentStatus.PROCESSING));
        when(persistence.claim(eq(1L), any(), any(), any())).thenReturn(Optional.empty());

        StepVerifier.create(processor.createAndProcess(REQUEST))
                .expectErrorSatisfies(e -> assertEquals(409, ((ResponseStatusException) e).getStatusCode().value()))
                .verify();

        verifyNoInteractions(accounts);
    }

    private static Payment payment(PaymentStatus status) {
        Payment p = new Payment();
        p.setId(1L);
        p.setFromAccount("A");
        p.setToAccount("B");
        p.setAmount(new BigDecimal("10.0000"));
        p.setIdempotencyKey("key-1");
        p.setStatus(status);
        return p;
    }

    private static WebClientResponseException http(int status) {
        return WebClientResponseException.create(status, "status " + status, HttpHeaders.EMPTY, new byte[0], null);
    }
}
