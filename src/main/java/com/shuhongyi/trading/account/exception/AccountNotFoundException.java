package com.shuhongyi.trading.account.exception;

import com.shuhongyi.trading.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class AccountNotFoundException extends ApiException {

    public AccountNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "account-not-found", message);
    }

    public static AccountNotFoundException forUser(Long userId) {
        return new AccountNotFoundException("No account found for user " + userId);
    }

    public static AccountNotFoundException forAccount(Long accountId) {
        return new AccountNotFoundException("Account not found: " + accountId);
    }
}
