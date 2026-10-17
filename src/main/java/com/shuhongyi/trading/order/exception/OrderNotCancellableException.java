package com.shuhongyi.trading.order.exception;

import com.shuhongyi.trading.common.error.ApiException;
import com.shuhongyi.trading.order.OrderStatus;
import org.springframework.http.HttpStatus;

public class OrderNotCancellableException extends ApiException {

    public OrderNotCancellableException(Long orderId, OrderStatus status) {
        super(HttpStatus.CONFLICT, "order-not-cancellable",
                "Order %d cannot be cancelled in status %s".formatted(orderId, status));
    }
}
