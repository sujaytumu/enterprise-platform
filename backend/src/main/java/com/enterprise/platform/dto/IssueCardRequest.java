package com.enterprise.platform.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class IssueCardRequest {
    @NotNull
    public UUID accountId;
}
