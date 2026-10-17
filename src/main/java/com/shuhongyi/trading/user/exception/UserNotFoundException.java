package com.shuhongyi.trading.user.exception;

import com.shuhongyi.trading.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ApiException {

    public UserNotFoundException(Long userId) {
        super(HttpStatus.NOT_FOUND, "user-not-found", "User not found: " + userId);
    }
}
