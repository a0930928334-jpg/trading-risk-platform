package com.shuhongyi.trading.position;

import com.shuhongyi.trading.position.exception.InsufficientPositionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.LocalDateTime;

/**
 * Shares of one instrument held by one account. {@code reservedQuantity} is earmarked for open sell orders.
 * Short selling is not allowed, so quantities never go negative.
 * Callers must hold a {@code PESSIMISTIC_WRITE} lock on the row before calling the mutating methods.
 */
@Entity
@Table(name = "positions")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(name = "instrument_id", nullable = false, updatable = false)
    private Long instrumentId;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "reserved_quantity", nullable = false)
    private long reservedQuantity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public long getAvailableQuantity() {
        return quantity - reservedQuantity;
    }

    void reserve(long shares) {
        requirePositive(shares);
        if (getAvailableQuantity() < shares) {
            throw new InsufficientPositionException(shares, getAvailableQuantity());
        }
        reservedQuantity += shares;
    }

    void release(long shares) {
        requirePositive(shares);
        if (reservedQuantity < shares) {
            throw new IllegalStateException("Cannot release %d shares; only %d reserved".formatted(shares, reservedQuantity));
        }
        reservedQuantity -= shares;
    }

    /** Delivers shares for a sell fill out of the quantity reserved when the order was placed. */
    void debitReserved(long shares) {
        requirePositive(shares);
        if (reservedQuantity < shares) {
            throw new IllegalStateException("Cannot deliver %d shares; only %d reserved".formatted(shares, reservedQuantity));
        }
        reservedQuantity -= shares;
        quantity -= shares;
    }

    void credit(long shares) {
        requirePositive(shares);
        quantity += shares;
    }

    private static void requirePositive(long shares) {
        if (shares <= 0) {
            throw new IllegalArgumentException("Share quantity must be positive: " + shares);
        }
    }
}
