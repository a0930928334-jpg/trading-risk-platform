package com.shuhongyi.trading.order;

import com.shuhongyi.trading.auth.CurrentUserId;
import com.shuhongyi.trading.common.web.PageResponse;
import com.shuhongyi.trading.common.web.Pagination;
import com.shuhongyi.trading.order.dto.OrderResponse;
import com.shuhongyi.trading.order.dto.PlaceOrderRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderCommandService orderCommandService;
    private final OrderQueryService orderQueryService;

    public OrderController(OrderCommandService orderCommandService, OrderQueryService orderQueryService) {
        this.orderCommandService = orderCommandService;
        this.orderQueryService = orderQueryService;
    }

    /**
     * Returns 201 for every order that passed request validation, including orders rejected by
     * pre-trade risk checks: those are stored with status {@code REJECTED} and a reason.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> place(
            @CurrentUserId Long userId,
            @Valid @RequestBody PlaceOrderRequest request) {
        OrderResponse response = orderCommandService.place(userId, request);
        return ResponseEntity.created(URI.create("/api/orders/" + response.id())).body(response);
    }

    @GetMapping
    public PageResponse<OrderResponse> list(
            @CurrentUserId Long userId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + Pagination.DEFAULT_SIZE) int size) {
        return orderQueryService.list(userId, status, Pagination.newestFirst(page, size));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@CurrentUserId Long userId, @PathVariable Long id) {
        return orderQueryService.get(userId, id);
    }

    @DeleteMapping("/{id}")
    public OrderResponse cancel(@CurrentUserId Long userId, @PathVariable Long id) {
        return orderCommandService.cancel(userId, id);
    }
}
