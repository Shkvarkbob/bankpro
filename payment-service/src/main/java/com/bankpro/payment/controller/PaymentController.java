package com.bankpro.payment.controller;

import com.bankpro.common.dto.TransferRequest;
import com.bankpro.payment.domain.Payment;
import com.bankpro.payment.service.PaymentProcessor;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentProcessor paymentProcessor;

    public PaymentController(final PaymentProcessor paymentProcessor) {this.paymentProcessor = paymentProcessor;}

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Payment> create(@Valid @RequestBody TransferRequest request) {
        return paymentProcessor.createAndProcess(request);
    }
}
