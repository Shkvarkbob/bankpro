package com.bankpro.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank @Size(max = 34) String accountNumber,
        @NotNull Long customerId,
        @DecimalMin("0.00") BigDecimal initialBalance
) {}
