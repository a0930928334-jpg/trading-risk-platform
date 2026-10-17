package com.shuhongyi.trading.order;

import com.shuhongyi.trading.auth.CurrentUserId;
import com.shuhongyi.trading.common.web.PageResponse;
import com.shuhongyi.trading.common.web.Pagination;
import com.shuhongyi.trading.order.dto.OrderResponse;
import com.shuhongyi.trading.order.dto.PlaceOrderRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Orders", description = "Limit orders (GTC). Placement runs pre-trade risk checks, reserves funds or shares, and matches immediately.")
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
    @Operation(summary = "Place a limit order", description = "Returns 201 for every well-formed order. Orders failing a risk check are stored with status REJECTED and a rejectCode; check the status field.")
    @ApiResponse(responseCode = "201", description = "Order stored (NEW, PARTIALLY_FILLED, FILLED, CANCELLED by self-trade prevention, or REJECTED)")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "404", description = "Unknown symbol")
    @ApiResponse(responseCode = "503", description = "Instrument busy")
    @PostMapping
    public ResponseEntity<OrderResponse> place(
            @CurrentUserId Long userId,
            @Valid @RequestBody PlaceOrderRequest request) {
        OrderResponse response = orderCommandService.place(userId, request);
        return ResponseEntity.created(URI.create("/api/orders/" + response.id())).body(response);
    }

    @Operation(summary = "List my orders", description = "Newest first, optionally filtered by status. Page size is capped at 100.")
    @ApiResponse(responseCode = "200", description = "Page of orders")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @GetMapping
    public PageResponse<OrderResponse> list(
            @CurrentUserId Long userId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + Pagination.DEFAULT_SIZE) int size) {
        return orderQueryService.list(userId, status, Pagination.newestFirst(page, size));
    }

    @Operation(summary = "Get one of my orders")
    @ApiResponse(responseCode = "200", description = "Order")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "404", description = "Not found or not yours")
    @GetMapping("/{id}")
    public OrderResponse get(@CurrentUserId Long userId, @PathVariable Long id) {
        return orderQueryService.get(userId, id);
    }

    @Operation(summary = "Cancel an open order", description = "Releases the reservation held by the unfilled quantity.")
    @ApiResponse(responseCode = "200", description = "Cancelled")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "404", description = "Not found or not yours")
    @ApiResponse(responseCode = "409", description = "Order is not open")
    @DeleteMapping("/{id}")
    public OrderResponse cancel(@CurrentUserId Long userId, @PathVariable Long id) {
        return orderCommandService.cancel(userId, id);
    }
}
