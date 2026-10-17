package com.shuhongyi.trading.account.exception;

import com.shuhongyi.trading.common.error.ApiException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class InsufficientFundsException extends ApiException {

    public InsufficientFundsException(BigDecimal required, BigDecimal available) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "insufficient-funds",
                "Insufficient available cash: required %s, available %s".formatted(
                        required.toPlainString(), available.toPlainString()));
    }
}
