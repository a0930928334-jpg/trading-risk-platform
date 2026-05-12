package com.shuhongyi.trading.system;

public record SystemStatusResponse(
        String application,
        String status,
        String phase
) {
}