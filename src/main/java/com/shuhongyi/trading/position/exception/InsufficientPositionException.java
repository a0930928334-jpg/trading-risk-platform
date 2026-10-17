package com.shuhongyi.trading.position.exception;

import com.shuhongyi.trading.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class InsufficientPositionException extends ApiException {

    public InsufficientPositionException(long required, long available) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "insufficient-position",
                "Insufficient available shares: required %d, available %d".formatted(required, available));
    }
}
