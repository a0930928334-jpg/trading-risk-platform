package com.shuhongyi.trading.account.exception;

import com.shuhongyi.trading.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class AccountNotActiveException extends ApiException {

    public AccountNotActiveException(Long accountId) {
        super(HttpStatus.CONFLICT, "account-not-active", "Account is not active: " + accountId);
    }
}
