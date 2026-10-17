package com.shuhongyi.trading.support;

public record TestUser(Long userId, Long accountId, String email, String accessToken) {

    public String bearer() {
        return "Bearer " + accessToken;
    }
}
