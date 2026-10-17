package com.shuhongyi.trading.auth.exception;

public class InvalidCredentialsException extends RuntimeException {

    private static final String MESSAGE = "Invalid email or password";

    public InvalidCredentialsException() {
        super(MESSAGE);
    }
}
