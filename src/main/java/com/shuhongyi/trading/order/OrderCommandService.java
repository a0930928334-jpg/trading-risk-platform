package com.shuhongyi.trading.order;

import com.shuhongyi.trading.account.Account;
import com.shuhongyi.trading.account.AccountRepository;
import com.shuhongyi.trading.account.AccountService;
import com.shuhongyi.trading.account.CashBalance;
import com.shuhongyi.trading.account.CashService;
import com.shuhongyi.trading.common.error.ApiException;
import com.shuhongyi.trading.common.money.Money;
import com.shuhongyi.trading.instrument.Instrument;
import com.shuhongyi.trading.instrument.InstrumentRepository;
import com.shuhongyi.trading.instrument.InstrumentService;
import com.shuhongyi.trading.matching.Fill;
import com.shuhongyi.trading.matching.IncomingOrder;
import com.shuhongyi.trading.matching.MatchResult;
import com.shuhongyi.trading.matching.MatchingEngine;
import com.shuhongyi.trading.matching.OrderBook;
import com.shuhongyi.trading.order.dto.OrderResponse;
import com.shuhongyi.trading.order.dto.PlaceOrderRequest;
import com.shuhongyi.trading.order.exception.OrderNotCancellableException;
import com.shuhongyi.trading.order.exception.OrderNotFoundException;
import com.shuhongyi.trading.position.Position;
import com.shuhongyi.trading.position.PositionService;
import com.shuhongyi.trading.risk.RiskContext;
import com.shuhongyi.trading.risk.RiskDecision;
import com.shuhongyi.trading.risk.RiskEngine;
import com.shuhongyi.trading.trade.SettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Places and cancels orders. Every command on an instrument follows the same sequence:
 *
 * <ol>
 *   <li>take the instrument lock (commands on one instrument are strictly serial);</li>
 *   <li>in one transaction: match against the in-memory book without changing it, lock the cash rows and
 *       then the position rows of every involved account in ascending account ID order, run the risk
 *       checks, and persist the order, trades, cash and positions;</li>
 *   <li>after the commit, apply the same match to the in-memory book.</li>
 * </ol>
 *
 * If the transaction fails unexpectedly, the instrument's book is rebuilt from the database,
 * which is the source of truth.
 */
@Service
public class OrderCommandService {

    public static final String SELF_TRADE_PREVENTED = "SELF_TRADE_PREVENTED";

    private static final Logger log = LoggerFactory.getLogger(OrderCommandService.class);

    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final InstrumentService instrumentService;
    private final InstrumentRepository instrumentRepository;
    private final OrderRepository orderRepository;
    private final CashService cashService;
    private final PositionService positionService;
    private final RiskEngine riskEngine;
    private final SettlementService settlementService;
    private final OrderBookRegistry orderBooks;
    private final InstrumentLockManager lockManager;
    private final TransactionTemplate transactionTemplate;
    private final MatchingEngine matchingEngine = new MatchingEngine();

    public OrderCommandService(
            AccountService accountService,
            AccountRepository accountRepository,
            InstrumentService instrumentService,
            InstrumentRepository instrumentRepository,
            OrderRepository orderRepository,
            CashService cashService,
            PositionService positionService,
            RiskEngine riskEngine,
            SettlementService settlementService,
            OrderBookRegistry orderBooks,
            InstrumentLockManager lockManager,
            TransactionTemplate transactionTemplate) {
        this.accountService = accountService;
        this.accountRepository = accountRepository;
        this.instrumentService = instrumentService;
        this.instrumentRepository = instrumentRepository;
        this.orderRepository = orderRepository;
        this.cashService = cashService;
        this.positionService = positionService;
        this.riskEngine = riskEngine;
        this.settlementService = settlementService;
        this.orderBooks = orderBooks;
        this.lockManager = lockManager;
        this.transactionTemplate = transactionTemplate;
    }

    public OrderResponse place(Long userId, PlaceOrderRequest request) {
        Long accountId = accountService.getAccountForUser(userId).getId();
        Instrument instrument = instrumentService.getBySymbol(request.symbol());
        Order order = placeForAccount(accountId, instrument.getId(), request);
        return OrderResponse.from(order, instrument.getSymbol());
    }

    /** Places an order for an account directly; used by the API and by the dev market maker. */
    public Order placeForAccount(Long accountId, Long instrumentId, PlaceOrderRequest request) {
        return lockManager.withLock(instrumentId, () -> {
            Placement placement = inTransaction(instrumentId, () -> placeInTransaction(accountId, instrumentId, request));
            if (placement.order().getStatus() != OrderStatus.REJECTED) {
                afterCommit(instrumentId, book ->
                        book.apply(placement.match(), placement.order().getId(), placement.incoming()));
            }
            return placement.order();
        });
    }

    public OrderResponse cancel(Long userId, Long orderId) {
        Long accountId = accountService.getAccountForUser(userId).getId();
        Order existing = orderRepository.findByIdAndAccountId(orderId, accountId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        Long instrumentId = existing.getInstrumentId();
        String symbol = instrumentRepository.findById(instrumentId).map(Instrument::getSymbol).orElseThrow();

        Order cancelled = lockManager.withLock(instrumentId, () -> {
            Order order = inTransaction(instrumentId, () -> cancelInTransaction(orderId));
            afterCommit(instrumentId, book -> book.remove(orderId));
            return order;
        });
        return OrderResponse.from(cancelled, symbol);
    }

    private Placement placeInTransaction(Long accountId, Long instrumentId, PlaceOrderRequest request) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        Instrument instrument = instrumentRepository.findById(instrumentId).orElseThrow();
        OrderSide side = request.side();
        BigDecimal price = Money.of(request.price());

        // 1. Match first, without touching the book, to learn which accounts this order trades with.
        IncomingOrder incoming = new IncomingOrder(accountId, side, price, request.quantity());
        MatchResult match = matchingEngine.match(orderBooks.book(instrumentId), incoming);

        // 2. Lock every involved row: all cash rows, then all position rows, each in ascending account ID order.
        Set<Long> participants = new TreeSet<>();
        participants.add(accountId);
        match.fills().forEach(fill -> participants.add(fill.makerAccountId()));
        Map<Long, CashBalance> cash = cashService.lockInOrder(participants);
        Map<Long, Position> positions = match.fills().isEmpty() && side == OrderSide.BUY
                ? Map.of()
                : positionService.lockInOrder(participants, instrumentId);

        // 3. Pre-trade risk on the locked state.
        RiskDecision decision = riskEngine.evaluate(new RiskContext(
                side, price, request.quantity(), account, instrument, cash.get(accountId), positions.get(accountId)));

        Order order = orderRepository.save(Order.builder()
                .accountId(accountId)
                .instrumentId(instrumentId)
                .side(side)
                .type(request.typeOrDefault())
                .timeInForce(request.timeInForceOrDefault())
                .price(price)
                .quantity(request.quantity())
                .status(decision.accepted() ? OrderStatus.NEW : OrderStatus.REJECTED)
                .rejectCode(decision.code())
                .rejectReason(decision.reason())
                .build());
        if (decision.rejected()) {
            return new Placement(order, incoming, match);
        }

        // 4. Reserve the full order, then settle each fill out of that reservation.
        if (side == OrderSide.BUY) {
            cashService.reserve(cash.get(accountId), order.getRemainingNotional(), order.getId());
        } else {
            positionService.reserve(positions.get(accountId), order.getQuantity());
        }

        for (Fill fill : match.fills()) {
            Order maker = orderRepository.findById(fill.makerOrderId()).orElseThrow();
            if (!maker.isOpen() || maker.getRemainingQuantity() < fill.quantity()) {
                throw new IllegalStateException("Order book is out of sync with the database for order " + maker.getId());
            }
            settlementService.settle(instrument, order, maker, fill.price(), fill.quantity(), cash, positions);
        }

        // 5. Self-trade prevention: cancel what is left of the new order and release its reservation.
        if (match.cancelledQuantity() > 0) {
            releaseRemaining(order, cash.get(accountId), positions.get(accountId));
            order.cancel(SELF_TRADE_PREVENTED,
                    "Remaining %d cancelled: it would have traded with your own resting order".formatted(
                            match.cancelledQuantity()));
        }
        return new Placement(orderRepository.saveAndFlush(order), incoming, match);
    }

    private Order cancelInTransaction(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.isOpen()) {
            throw new OrderNotCancellableException(orderId, order.getStatus());
        }
        CashBalance cash = order.getSide() == OrderSide.BUY ? cashService.lock(order.getAccountId()) : null;
        Position position = order.getSide() == OrderSide.SELL
                ? positionService.lock(order.getAccountId(), order.getInstrumentId())
                : null;
        releaseRemaining(order, cash, position);
        order.cancel();
        return orderRepository.saveAndFlush(order);
    }

    private void releaseRemaining(Order order, CashBalance cash, Position position) {
        if (order.getSide() == OrderSide.BUY) {
            cashService.release(cash, order.getRemainingNotional(), order.getId());
        } else {
            positionService.release(position, order.getRemainingQuantity());
        }
    }

    /**
     * Runs work in a transaction. An unexpected failure means the in-memory book may no longer match
     * the database, so the book is rebuilt from the database before the error propagates.
     * Business exceptions ({@link ApiException}) are expected outcomes and need no rebuild.
     */
    private <T> T inTransaction(Long instrumentId, Supplier<T> work) {
        try {
            return transactionTemplate.execute(status -> work.get());
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Transaction failed for instrument {}; rebuilding its order book from the database",
                    instrumentId, e);
            orderBooks.rebuild(instrumentId);
            throw e;
        }
    }

    private void afterCommit(Long instrumentId, Consumer<OrderBook> change) {
        try {
            change.accept(orderBooks.book(instrumentId));
        } catch (RuntimeException e) {
            log.error("Could not apply committed change to the order book of instrument {}; rebuilding it",
                    instrumentId, e);
            orderBooks.rebuild(instrumentId);
        }
    }

    private record Placement(Order order, IncomingOrder incoming, MatchResult match) {
    }
}
