package com.enterprise.platform.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class CreateAccountRequest {
    @NotBlank
    @Size(max = 100)
    public String holderName;

    @NotNull @Positive
    @Digits(integer = 12, fraction = 2)
    @DecimalMax("1000000000.00")
    public BigDecimal openingBalance;

    // ISO 4217-style code, e.g. USD. Optional; defaults to USD.
    @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter uppercase currency code")
    public String currency = "USD";
}
