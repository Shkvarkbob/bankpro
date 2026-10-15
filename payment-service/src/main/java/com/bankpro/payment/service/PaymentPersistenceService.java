package com.bankpro.payment.service;

import com.bankpro.common.dto.TransferRequest;
import com.bankpro.payment.domain.Payment;
import com.bankpro.payment.domain.PaymentStatus;
import com.bankpro.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

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
                    return paymentRepository.save(p);
                });
    }

    @Transactional(readOnly = true)
    public Payment findByIdempotencyKey(String idempotencyKey) {
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Payment not found: " + idempotencyKey));
    }

    /**
     * Moves the payment from {@code expected} to {@code target} if it still has that status and has not
     * been touched during the last {@code minAge}. Returns empty if somebody else got there first.
     */
    @Transactional
    public Optional<Payment> claim(Long id, PaymentStatus expected, PaymentStatus target, Duration minAge) {
        OffsetDateTime now = OffsetDateTime.now();
        int rows = paymentRepository.claim(id, expected, target, now, now.minus(minAge));
        return rows == 0 ? Optional.empty() : paymentRepository.findById(id);
    }

    @Transactional
    public Payment updateStatus(Long id, PaymentStatus status) {
        paymentRepository.updateStatus(id, status, OffsetDateTime.now());
        return paymentRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Payment not found: " + id));
    }
}
