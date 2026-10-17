package com.shuhongyi.trading.account;

import com.shuhongyi.trading.account.exception.InsufficientFundsException;
import com.shuhongyi.trading.common.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cash held by an account. {@code reserved} is the part earmarked for open buy orders;
 * {@code available = balance - reserved} is what new orders may use.
 * Callers must hold a {@code PESSIMISTIC_WRITE} lock on the row before calling the mutating methods.
 */
@Entity
@Table(name = "cash_balances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Builder.Default
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal reserved = Money.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public BigDecimal getAvailable() {
        return balance.subtract(reserved);
    }

    void deposit(BigDecimal amount) {
        requirePositive(amount);
        balance = balance.add(amount);
    }

    void reserve(BigDecimal amount) {
        requirePositive(amount);
        if (getAvailable().compareTo(amount) < 0) {
            throw new InsufficientFundsException(amount, getAvailable());
        }
        reserved = reserved.add(amount);
    }

    void release(BigDecimal amount) {
        requirePositive(amount);
        if (reserved.compareTo(amount) < 0) {
            throw new IllegalStateException("Cannot release %s; only %s reserved".formatted(amount, reserved));
        }
        reserved = reserved.subtract(amount);
    }

    /** Pays for a buy fill out of funds that were reserved when the order was placed. */
    void debitReserved(BigDecimal amount) {
        requirePositive(amount);
        if (reserved.compareTo(amount) < 0) {
            throw new IllegalStateException("Cannot debit %s; only %s reserved".formatted(amount, reserved));
        }
        reserved = reserved.subtract(amount);
        balance = balance.subtract(amount);
    }

    void credit(BigDecimal amount) {
        requirePositive(amount);
        balance = balance.add(amount);
    }

    private static void requirePositive(BigDecimal amount) {
        if (!Money.isPositive(amount)) {
            throw new IllegalArgumentException("Amount must be positive: " + amount);
        }
    }
}
