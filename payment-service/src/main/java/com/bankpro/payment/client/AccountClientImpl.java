package com.bankpro.payment.client;

import com.bankpro.common.dto.TransferRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class AccountClientImpl implements AccountClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;

    public AccountClientImpl(WebClient webClient) {this.webClient = webClient;}

    @Override
    public Mono<Void> debit(TransferRequest request) {
        return post("/api/v1/accounts/debit", request);
    }

    @Override
    public Mono<Void> credit(TransferRequest request) {
        return post("/api/v1/accounts/credit", request);
    }

    @Override
    public Mono<Void> reverseDebit(TransferRequest request) {
        return post("/api/v1/accounts/reverse-debit", request);
    }

    private Mono<Void> post(String uri, TransferRequest request) {
        return webClient.post().uri(uri).bodyValue(request).retrieve().bodyToMono(Void.class).timeout(TIMEOUT);
    }
}
