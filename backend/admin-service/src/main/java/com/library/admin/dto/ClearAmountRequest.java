package com.library.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

// Shared by clear-dues and clear-pending-fees — the admin enters how much is
// actually being collected (prepopulated in the UI with the full outstanding
// amount); any remainder is carried forward as a new pending balance rather
// than being wiped out.
@Data
public class ClearAmountRequest {
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amountCleared;
}
