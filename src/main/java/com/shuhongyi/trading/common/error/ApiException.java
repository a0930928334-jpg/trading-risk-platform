package com.shuhongyi.trading.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base class for business exceptions that map to an RFC 9457 problem response.
 * Each module defines its own subclasses; {@link GlobalExceptionHandler} renders them.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
