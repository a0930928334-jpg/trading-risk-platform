package com.shuhongyi.trading.order;

import com.shuhongyi.trading.common.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A limit order. The database row is the source of truth; the in-memory order book only mirrors open orders.
 * The ID is the time-priority sequence: a lower ID arrived earlier.
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(name = "instrument_id", nullable = false, updatable = false)
    private Long instrumentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, updatable = false)
    private OrderType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_in_force", nullable = false, updatable = false)
    private TimeInForce timeInForce;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal price;

    @Column(nullable = false, updatable = false)
    private long quantity;

    @Column(name = "filled_quantity", nullable = false)
    private long filledQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "reject_code", length = 64)
    private String rejectCode;

    @Column(name = "reject_reason")
    private String rejectReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public long getRemainingQuantity() {
        return quantity - filledQuantity;
    }

    public boolean isOpen() {
        return status.isOpen();
    }

    /** Cash a buy order still holds in reservation: remaining quantity at the limit price. */
    public BigDecimal getRemainingNotional() {
        return Money.notional(getRemainingQuantity(), price);
    }

    public void fill(long shares) {
        if (!isOpen()) {
            throw new IllegalStateException("Cannot fill order %d in status %s".formatted(id, status));
        }
        if (shares <= 0 || shares > getRemainingQuantity()) {
            throw new IllegalArgumentException(
                    "Invalid fill of %d shares for order %d with %d remaining".formatted(shares, id, getRemainingQuantity()));
        }
        filledQuantity += shares;
        status = filledQuantity == quantity ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
    }

    public void cancel() {
        if (!isOpen()) {
            throw new IllegalStateException("Cannot cancel order %d in status %s".formatted(id, status));
        }
        status = OrderStatus.CANCELLED;
    }
}
