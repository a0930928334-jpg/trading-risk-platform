package com.shuhongyi.trading.risk;

import com.shuhongyi.trading.account.Account;
import com.shuhongyi.trading.account.AccountStatus;
import com.shuhongyi.trading.account.CashBalance;
import com.shuhongyi.trading.instrument.Instrument;
import com.shuhongyi.trading.instrument.InstrumentStatus;
import com.shuhongyi.trading.order.OrderSide;
import com.shuhongyi.trading.position.Position;

import java.math.BigDecimal;

/**
 * Builds {@link RiskContext}s for rule unit tests. Defaults describe an order that every rule accepts.
 */
public final class RiskContexts {

    private OrderSide side = OrderSide.BUY;
    private BigDecimal price = new BigDecimal("100.0000");
    private long quantity = 10;
    private AccountStatus accountStatus = AccountStatus.ACTIVE;
    private InstrumentStatus instrumentStatus = InstrumentStatus.ACTIVE;
    private BigDecimal referencePrice = new BigDecimal("100.0000");
    private BigDecimal lastTradePrice;
    private BigDecimal cashBalance = new BigDecimal("1000000.0000");
    private BigDecimal cashReserved = BigDecimal.ZERO;
    private Long positionQuantity = 1_000L;
    private long positionReserved;

    public static RiskContexts order() {
        return new RiskContexts();
    }

    public RiskContexts side(OrderSide side) {
        this.side = side;
        return this;
    }

    public RiskContexts price(String price) {
        this.price = new BigDecimal(price);
        return this;
    }

    public RiskContexts quantity(long quantity) {
        this.quantity = quantity;
        return this;
    }

    public RiskContexts accountStatus(AccountStatus status) {
        this.accountStatus = status;
        return this;
    }

    public RiskContexts instrumentStatus(InstrumentStatus status) {
        this.instrumentStatus = status;
        return this;
    }

    public RiskContexts referencePrice(String price) {
        this.referencePrice = new BigDecimal(price);
        return this;
    }

    public RiskContexts lastTradePrice(String price) {
        this.lastTradePrice = new BigDecimal(price);
        return this;
    }

    public RiskContexts cash(String balance, String reserved) {
        this.cashBalance = new BigDecimal(balance);
        this.cashReserved = new BigDecimal(reserved);
        return this;
    }

    public RiskContexts position(long quantity, long reserved) {
        this.positionQuantity = quantity;
        this.positionReserved = reserved;
        return this;
    }

    public RiskContexts noPosition() {
        this.positionQuantity = null;
        return this;
    }

    public RiskContext build() {
        Account account = Account.builder().id(1L).status(accountStatus).currency("USD").build();
        Instrument instrument = Instrument.builder()
                .id(1L)
                .symbol("TEST")
                .status(instrumentStatus)
                .referencePrice(referencePrice)
                .lastTradePrice(lastTradePrice)
                .build();
        CashBalance cash = CashBalance.builder().balance(cashBalance).reserved(cashReserved).currency("USD").build();
        Position position = positionQuantity == null ? null : Position.builder()
                .accountId(1L)
                .instrumentId(1L)
                .quantity(positionQuantity)
                .reservedQuantity(positionReserved)
                .build();
        return new RiskContext(side, price, quantity, account, instrument, cash, position);
    }
}
