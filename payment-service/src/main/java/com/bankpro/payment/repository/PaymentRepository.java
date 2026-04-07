package com.bankpro.payment.repository;

import com.bankpro.payment.domain.Payment;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface PaymentRepository extends CrudRepository<Payment, Long> {
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
