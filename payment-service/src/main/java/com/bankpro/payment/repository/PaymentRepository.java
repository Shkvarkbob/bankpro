package com.bankpro.payment.repository;

import com.bankpro.payment.domain.Payment;
import com.bankpro.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface PaymentRepository extends CrudRepository<Payment, Long> {
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /** Atomic compare-and-set on the status: only one concurrent caller gets 1 back. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Payment p set p.status = :target, p.updatedAt = :now "
            + "where p.id = :id and p.status = :expected and p.updatedAt <= :threshold")
    int claim(@Param("id") Long id,
              @Param("expected") PaymentStatus expected,
              @Param("target") PaymentStatus target,
              @Param("now") OffsetDateTime now,
              @Param("threshold") OffsetDateTime threshold);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Payment p set p.status = :status, p.updatedAt = :now where p.id = :id")
    int updateStatus(@Param("id") Long id,
                     @Param("status") PaymentStatus status,
                     @Param("now") OffsetDateTime now);
}
