package com.shuhongyi.trading.user;

public enum UserStatus {
    ACTIVE,
    DISABLED,
    /** Internal account (e.g. the dev market maker). Cannot log in. */
    SYSTEM
}
