package com.shuhongyi.trading.marketmaker;

import com.shuhongyi.trading.account.Account;
import com.shuhongyi.trading.instrument.Instrument;
import com.shuhongyi.trading.instrument.InstrumentRepository;
import com.shuhongyi.trading.instrument.InstrumentStatus;
import com.shuhongyi.trading.order.Order;
import com.shuhongyi.trading.order.OrderCommandService;
import com.shuhongyi.trading.order.OrderRepository;
import com.shuhongyi.trading.order.OrderSide;
import com.shuhongyi.trading.order.OrderStatus;
import com.shuhongyi.trading.order.OrderType;
import com.shuhongyi.trading.order.TimeInForce;
import com.shuhongyi.trading.order.dto.PlaceOrderRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;

/**
 * Places a ladder of bids below and asks above the mark price of every active instrument.
 * Bids and asks never cross. Instruments where the market maker already has open orders
 * (for example after a restart) are left alone.
 */
@Component
public class MarketMakerQuoter {

    private static final Logger log = LoggerFactory.getLogger(MarketMakerQuoter.class);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final MarketMakerProperties properties;
    private final InstrumentRepository instrumentRepository;
    private final OrderRepository orderRepository;
    private final OrderCommandService orderCommandService;

    public MarketMakerQuoter(
            MarketMakerProperties properties,
            InstrumentRepository instrumentRepository,
            OrderRepository orderRepository,
            OrderCommandService orderCommandService) {
        this.properties = properties;
        this.instrumentRepository = instrumentRepository;
        this.orderRepository = orderRepository;
        this.orderCommandService = orderCommandService;
    }

    /** Returns the number of orders placed. */
    public int quote(Account account) {
        int placed = 0;
        for (Instrument instrument : instrumentRepository.findAll()) {
            if (instrument.getStatus() != InstrumentStatus.ACTIVE || hasOpenOrders(account, instrument)) {
                continue;
            }
            BigDecimal mark = instrument.getMarkPrice();
            for (int level = 1; level <= properties.quoteLevels(); level++) {
                BigDecimal offset = properties.quoteStepPercent().multiply(BigDecimal.valueOf(level)).divide(HUNDRED);
                placed += place(account, instrument, OrderSide.BUY, mark.multiply(BigDecimal.ONE.subtract(offset)));
                placed += place(account, instrument, OrderSide.SELL, mark.multiply(BigDecimal.ONE.add(offset)));
            }
        }
        return placed;
    }

    private boolean hasOpenOrders(Account account, Instrument instrument) {
        return orderRepository.existsByAccountIdAndInstrumentIdAndStatusIn(
                account.getId(), instrument.getId(), EnumSet.of(OrderStatus.NEW, OrderStatus.PARTIALLY_FILLED));
    }

    private int place(Account account, Instrument instrument, OrderSide side, BigDecimal rawPrice) {
        BigDecimal price = rawPrice.setScale(2, RoundingMode.HALF_UP);
        Order order = orderCommandService.placeForAccount(account.getId(), instrument.getId(), new PlaceOrderRequest(
                instrument.getSymbol(), side, OrderType.LIMIT, TimeInForce.GTC, price, properties.quoteQuantity()));
        if (order.getStatus() == OrderStatus.REJECTED) {
            log.warn("Market maker quote {} {} @ {} rejected: {}", side, instrument.getSymbol(), price, order.getRejectReason());
            return 0;
        }
        return 1;
    }
}
