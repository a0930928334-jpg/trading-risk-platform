package com.shuhongyi.trading.order;

import com.shuhongyi.trading.account.Account;
import com.shuhongyi.trading.account.AccountRepository;
import com.shuhongyi.trading.account.AccountService;
import com.shuhongyi.trading.account.CashBalance;
import com.shuhongyi.trading.account.CashService;
import com.shuhongyi.trading.common.money.Money;
import com.shuhongyi.trading.instrument.Instrument;
import com.shuhongyi.trading.instrument.InstrumentRepository;
import com.shuhongyi.trading.instrument.InstrumentService;
import com.shuhongyi.trading.order.dto.OrderResponse;
import com.shuhongyi.trading.order.dto.PlaceOrderRequest;
import com.shuhongyi.trading.order.exception.OrderNotCancellableException;
import com.shuhongyi.trading.order.exception.OrderNotFoundException;
import com.shuhongyi.trading.position.Position;
import com.shuhongyi.trading.position.PositionService;
import com.shuhongyi.trading.risk.RiskContext;
import com.shuhongyi.trading.risk.RiskDecision;
import com.shuhongyi.trading.risk.RiskEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Places and cancels orders. Each command takes the instrument lock first and then runs one database
 * transaction, so commands on the same instrument are strictly serial.
 */
@Service
public class OrderCommandService {

    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final InstrumentService instrumentService;
    private final InstrumentRepository instrumentRepository;
    private final OrderRepository orderRepository;
    private final CashService cashService;
    private final PositionService positionService;
    private final RiskEngine riskEngine;
    private final InstrumentLockManager lockManager;
    private final TransactionTemplate transactionTemplate;

    public OrderCommandService(
            AccountService accountService,
            AccountRepository accountRepository,
            InstrumentService instrumentService,
            InstrumentRepository instrumentRepository,
            OrderRepository orderRepository,
            CashService cashService,
            PositionService positionService,
            RiskEngine riskEngine,
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
        this.lockManager = lockManager;
        this.transactionTemplate = transactionTemplate;
    }

    public OrderResponse place(Long userId, PlaceOrderRequest request) {
        Long accountId = accountService.getAccountForUser(userId).getId();
        Instrument instrument = instrumentService.getBySymbol(request.symbol());

        Order order = lockManager.withLock(instrument.getId(), () ->
                transactionTemplate.execute(status -> placeInTransaction(accountId, instrument.getId(), request)));
        return OrderResponse.from(order, instrument.getSymbol());
    }

    public OrderResponse cancel(Long userId, Long orderId) {
        Long accountId = accountService.getAccountForUser(userId).getId();
        Order existing = orderRepository.findByIdAndAccountId(orderId, accountId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        Instrument instrument = instrumentRepository.findById(existing.getInstrumentId()).orElseThrow();

        Order cancelled = lockManager.withLock(instrument.getId(), () ->
                transactionTemplate.execute(status -> cancelInTransaction(orderId)));
        return OrderResponse.from(cancelled, instrument.getSymbol());
    }

    private Order placeInTransaction(Long accountId, Long instrumentId, PlaceOrderRequest request) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        Instrument instrument = instrumentRepository.findById(instrumentId).orElseThrow();
        OrderSide side = request.side();
        var price = Money.of(request.price());

        CashBalance cash = cashService.lock(accountId);
        Position position = side == OrderSide.SELL ? positionService.lock(accountId, instrumentId) : null;

        RiskDecision decision = riskEngine.evaluate(
                new RiskContext(side, price, request.quantity(), account, instrument, cash, position));

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
            return order;
        }

        if (side == OrderSide.BUY) {
            cashService.reserve(cash, order.getRemainingNotional(), order.getId());
        } else {
            positionService.reserve(position, order.getQuantity());
        }
        return order;
    }

    private Order cancelInTransaction(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.isOpen()) {
            throw new OrderNotCancellableException(orderId, order.getStatus());
        }

        if (order.getSide() == OrderSide.BUY) {
            CashBalance cash = cashService.lock(order.getAccountId());
            cashService.release(cash, order.getRemainingNotional(), order.getId());
        } else {
            Position position = positionService.lock(order.getAccountId(), order.getInstrumentId());
            positionService.release(position, order.getRemainingQuantity());
        }
        order.cancel();
        return orderRepository.saveAndFlush(order);
    }
}
