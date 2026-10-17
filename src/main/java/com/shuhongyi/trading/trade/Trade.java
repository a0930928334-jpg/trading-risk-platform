package com.shuhongyi.trading.trade;

import com.shuhongyi.trading.order.OrderSide;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** An execution between one buy order and one sell order. Immutable once written. */
@Entity
@Table(name = "trades")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instrument_id", nullable = false, updatable = false)
    private Long instrumentId;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal price;

    @Column(nullable = false, updatable = false)
    private long quantity;

    @Column(name = "buy_order_id", nullable = false, updatable = false)
    private Long buyOrderId;

    @Column(name = "sell_order_id", nullable = false, updatable = false)
    private Long sellOrderId;

    @Column(name = "buyer_account_id", nullable = false, updatable = false)
    private Long buyerAccountId;

    @Column(name = "seller_account_id", nullable = false, updatable = false)
    private Long sellerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggressor_side", nullable = false, updatable = false)
    private OrderSide aggressorSide;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
