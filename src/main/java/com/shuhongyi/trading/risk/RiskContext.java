package com.shuhongyi.trading.risk;

import com.shuhongyi.trading.account.Account;
import com.shuhongyi.trading.account.CashBalance;
import com.shuhongyi.trading.common.money.Money;
import com.shuhongyi.trading.instrument.Instrument;
import com.shuhongyi.trading.order.OrderSide;
import com.shuhongyi.trading.position.Position;

import java.math.BigDecimal;

/**
 * Everything a pre-trade rule may inspect. The cash and position rows are already locked by the caller,
 * so the values rules see cannot change before the order's reservation is written.
 *
 * @param position the account's position in the instrument; may be {@code null} for buy orders
 */
public record RiskContext(
        OrderSide side,
        BigDecimal price,
        long quantity,
        Account account,
        Instrument instrument,
        CashBalance cash,
        Position position
) {

    public BigDecimal notional() {
        return Money.notional(quantity, price);
    }
}
