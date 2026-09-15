package com.enterprise.platform.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public class AuthorizeRequest {
    @NotNull
    public UUID accountId;
    public UUID cardId; // optional
    @NotNull @Positive
    public BigDecimal amount;
    public String merchant;
}
