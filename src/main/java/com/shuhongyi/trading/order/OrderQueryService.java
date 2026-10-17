package com.shuhongyi.trading.order;

import com.shuhongyi.trading.account.AccountService;
import com.shuhongyi.trading.common.web.PageResponse;
import com.shuhongyi.trading.instrument.Instrument;
import com.shuhongyi.trading.instrument.InstrumentRepository;
import com.shuhongyi.trading.order.dto.OrderResponse;
import com.shuhongyi.trading.order.exception.OrderNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderQueryService {

    private final AccountService accountService;
    private final OrderRepository orderRepository;
    private final InstrumentRepository instrumentRepository;

    public OrderQueryService(
            AccountService accountService,
            OrderRepository orderRepository,
            InstrumentRepository instrumentRepository) {
        this.accountService = accountService;
        this.orderRepository = orderRepository;
        this.instrumentRepository = instrumentRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(Long userId, OrderStatus status, Pageable pageable) {
        Long accountId = accountService.getAccountForUser(userId).getId();
        Page<Order> orders = status == null
                ? orderRepository.findByAccountId(accountId, pageable)
                : orderRepository.findByAccountIdAndStatus(accountId, status, pageable);

        Map<Long, String> symbols = instrumentRepository.findAllById(orders.map(Order::getInstrumentId).toSet())
                .stream()
                .collect(Collectors.toMap(Instrument::getId, Instrument::getSymbol));
        return PageResponse.from(orders, order -> OrderResponse.from(order, symbols.get(order.getInstrumentId())));
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long userId, Long orderId) {
        Long accountId = accountService.getAccountForUser(userId).getId();
        Order order = orderRepository.findByIdAndAccountId(orderId, accountId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        String symbol = instrumentRepository.findById(order.getInstrumentId()).map(Instrument::getSymbol).orElseThrow();
        return OrderResponse.from(order, symbol);
    }
}
