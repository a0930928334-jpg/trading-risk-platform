package com.shuhongyi.trading.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @Schema(example = "alice@example.com") @NotBlank String email,
        @Schema(example = "Password123!") @NotBlank String password,
        @Schema(example = "Alice Example") @NotBlank String fullName
) {
}
