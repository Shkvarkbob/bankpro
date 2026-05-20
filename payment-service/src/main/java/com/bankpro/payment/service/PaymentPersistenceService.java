package com.bankpro.payment.service;

import com.bankpro.common.dto.TransferRequest;
import com.bankpro.payment.domain.Payment;
import com.bankpro.payment.domain.PaymentStatus;
import com.bankpro.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class PaymentPersistenceService {

    private final PaymentRepository paymentRepository;

    public PaymentPersistenceService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Payment findOrCreate(TransferRequest request) {
        return paymentRepository.findByIdempotencyKey(request.idempotencyKey())
                .orElseGet(() -> {
                    Payment p = new Payment();
                    p.setFromAccount(request.fromAccount());
                    p.setToAccount(request.toAccount());
                    p.setAmount(request.amount());
                    p.setIdempotencyKey(request.idempotencyKey());
                    p.setStatus(PaymentStatus.CREATED);
                    p.setCreatedAt(OffsetDateTime.now());
                    return paymentRepository.save(p);
                });
    }

    public Payment findByIdempotencyKey(String idempotencyKey) {
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Payment not found: " + idempotencyKey));
    }

    @Transactional
    public Payment markProcessing(Payment payment) {
        payment.setStatus(PaymentStatus.PROCESSING);
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment markCompleted(Payment payment) {
        payment.setStatus(PaymentStatus.COMPLETED);
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment markFailed(Payment payment) {
        payment.setStatus(PaymentStatus.FAILED);
        return paymentRepository.save(payment);
    }
}
