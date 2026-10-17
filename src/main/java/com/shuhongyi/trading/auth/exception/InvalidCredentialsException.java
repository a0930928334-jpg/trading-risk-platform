package com.shuhongyi.trading.auth.exception;

import com.shuhongyi.trading.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends ApiException {

    private static final String MESSAGE = "Invalid email or password";

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "invalid-credentials", MESSAGE);
    }
}
