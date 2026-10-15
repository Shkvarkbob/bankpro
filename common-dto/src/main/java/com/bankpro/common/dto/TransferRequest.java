package com.bankpro.common.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record TransferRequest (
        @NotBlank @Size(max = 34) String fromAccount,
        @NotBlank @Size(max = 34) String toAccount,
        @NotNull
        @DecimalMin("0.01")
        BigDecimal amount,
        @NotBlank @Size(max = 64) String idempotencyKey
) {}
