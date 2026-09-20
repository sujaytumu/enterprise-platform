package com.enterprise.platform.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public class TransferRequest {
    @NotNull
    public UUID fromAccountId;
    @NotNull
    public UUID toAccountId;
    @NotNull @Positive
    public BigDecimal amount;
    public String note;
}
