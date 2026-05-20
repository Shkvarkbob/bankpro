package com.bankpro.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${account.service.url:http://account-service:8081}")
    private String accountServiceUrl;

    @Bean
    public WebClient accountWebClient(WebClient.Builder builder) {
        return builder.baseUrl(accountServiceUrl).build();
    }
}
