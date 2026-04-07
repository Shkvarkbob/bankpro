package com.bankpro.payment.client;

import com.bankpro.common.dto.TransferRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class AccountClientImpl implements AccountClient {

    private final WebClient webClient;

    public AccountClientImpl(WebClient webClient) {this.webClient = webClient;}

    @Override
    public Mono<Void> debit(TransferRequest request) {
        return webClient.post().uri("/api/v1/accounts/debit").bodyValue(request).retrieve().bodyToMono(Void.class);
    }

    @Override
    public Mono<Void> credit(TransferRequest request) {
        return webClient.post().uri("/api/v1/accounts/credit").bodyValue(request).retrieve().bodyToMono(Void.class);
    }
}
