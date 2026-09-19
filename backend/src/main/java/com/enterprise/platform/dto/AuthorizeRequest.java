package com.enterprise.platform.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public class AuthorizeRequest {
    @NotNull
    public UUID accountId;
    public UUID cardId; // optional

    @NotNull @Positive
    @Digits(integer = 12, fraction = 2)
    @DecimalMax("1000000000.00")
    public BigDecimal amount;

    @Size(max = 100)
    public String merchant;
}
