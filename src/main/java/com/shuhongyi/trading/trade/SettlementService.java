package com.shuhongyi.trading.trade;

import com.shuhongyi.trading.account.CashBalance;
import com.shuhongyi.trading.account.CashService;
import com.shuhongyi.trading.common.money.Money;
import com.shuhongyi.trading.instrument.Instrument;
import com.shuhongyi.trading.order.Order;
import com.shuhongyi.trading.order.OrderSide;
import com.shuhongyi.trading.position.Position;
import com.shuhongyi.trading.position.PositionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Records one execution and moves cash and shares between the two accounts.
 * The caller has already locked every involved cash and position row (in ascending account ID order).
 *
 * <p>Buyer: pays {@code quantity x price} out of reserved cash, and if the buy order was the taker and
 * traded below its limit, the unused part of its reservation is released. Receives the shares.
 * <br>Seller: delivers reserved shares and receives the cash.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class SettlementService {

    private final TradeRepository tradeRepository;
    private final CashService cashService;
    private final PositionService positionService;

    public SettlementService(TradeRepository tradeRepository, CashService cashService, PositionService positionService) {
        this.tradeRepository = tradeRepository;
        this.cashService = cashService;
        this.positionService = positionService;
    }

    public Trade settle(
            Instrument instrument,
            Order taker,
            Order maker,
            BigDecimal price,
            long quantity,
            Map<Long, CashBalance> lockedCash,
            Map<Long, Position> lockedPositions) {
        Order buy = taker.getSide() == OrderSide.BUY ? taker : maker;
        Order sell = taker.getSide() == OrderSide.SELL ? taker : maker;

        Trade trade = tradeRepository.save(Trade.builder()
                .instrumentId(instrument.getId())
                .price(price)
                .quantity(quantity)
                .buyOrderId(buy.getId())
                .sellOrderId(sell.getId())
                .buyerAccountId(buy.getAccountId())
                .sellerAccountId(sell.getAccountId())
                .aggressorSide(taker.getSide())
                .build());

        taker.fill(quantity);
        maker.fill(quantity);

        BigDecimal value = Money.notional(quantity, price);
        // The buy order reserved quantity x its own limit price; anything above the trade price is released.
        BigDecimal priceImprovement = Money.notional(quantity, buy.getPrice()).subtract(value);

        CashBalance buyerCash = lockedCash.get(buy.getAccountId());
        cashService.debitReserved(buyerCash, value, buy.getId(), trade.getId());
        if (priceImprovement.signum() > 0) {
            cashService.release(buyerCash, priceImprovement, buy.getId());
        }
        positionService.credit(lockedPositions.get(buy.getAccountId()), quantity);

        positionService.debitReserved(lockedPositions.get(sell.getAccountId()), quantity);
        cashService.credit(lockedCash.get(sell.getAccountId()), value, sell.getId(), trade.getId());

        instrument.setLastTradePrice(price);
        return trade;
    }
}
