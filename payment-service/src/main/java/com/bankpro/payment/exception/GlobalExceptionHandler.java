package com.bankpro.payment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, String>>> handleValidation(WebExchangeBindException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return Mono.just(ResponseEntity.badRequest().body(Map.of("error", msg)));
    }

    /** Business errors of the account service (4xx) are passed through; anything else is a bad gateway. */
    @ExceptionHandler(WebClientResponseException.class)
    public Mono<ResponseEntity<String>> handleUpstreamError(WebClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String body = ex.getResponseBodyAsString();
        if (status.is4xxClientError() && !body.isBlank()) {
            return Mono.just(ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body));
        }
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"Account service error\"}"));
    }

    @ExceptionHandler(WebClientRequestException.class)
    public Mono<ResponseEntity<Map<String, String>>> handleUpstreamUnavailable(WebClientRequestException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "Account service unavailable")));
    }

    @ExceptionHandler(TimeoutException.class)
    public Mono<ResponseEntity<Map<String, String>>> handleTimeout(TimeoutException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(Map.of("error", "Account service timed out")));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<Map<String, String>>> handleResponseStatus(ResponseStatusException ex) {
        String reason = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return Mono.just(ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", reason)));
    }

    @ExceptionHandler(IllegalStateException.class)
    public Mono<ResponseEntity<Map<String, String>>> handleIllegalState(IllegalStateException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", ex.getMessage())));
    }
}
