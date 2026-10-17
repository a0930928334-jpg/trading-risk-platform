package com.shuhongyi.trading.auth.exception;

import com.shuhongyi.trading.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class DuplicateEmailException extends ApiException {

    public DuplicateEmailException(String email) {
        super(HttpStatus.CONFLICT, "duplicate-email", "Email already registered: " + email);
    }
}
