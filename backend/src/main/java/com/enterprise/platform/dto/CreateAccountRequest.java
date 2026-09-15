package com.enterprise.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public class CreateAccountRequest {
    @NotBlank
    public String holderName;
    @NotNull @Positive
    public BigDecimal openingBalance;
    public String currency = "USD";
}
