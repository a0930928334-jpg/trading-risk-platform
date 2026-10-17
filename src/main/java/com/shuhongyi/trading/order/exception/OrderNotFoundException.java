package com.shuhongyi.trading.order.exception;

import com.shuhongyi.trading.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** Also used for orders that belong to another account, so their existence is not revealed. */
public class OrderNotFoundException extends ApiException {

    public OrderNotFoundException(Long orderId) {
        super(HttpStatus.NOT_FOUND, "order-not-found", "Order not found: " + orderId);
    }
}
