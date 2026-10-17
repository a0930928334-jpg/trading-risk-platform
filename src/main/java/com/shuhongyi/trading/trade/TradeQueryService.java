package com.shuhongyi.trading.trade;

import com.shuhongyi.trading.account.AccountService;
import com.shuhongyi.trading.common.money.Money;
import com.shuhongyi.trading.common.web.PageResponse;
import com.shuhongyi.trading.instrument.Instrument;
import com.shuhongyi.trading.instrument.InstrumentRepository;
import com.shuhongyi.trading.order.OrderSide;
import com.shuhongyi.trading.trade.dto.TradeResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TradeQueryService {

    private final AccountService accountService;
    private final TradeRepository tradeRepository;
    private final InstrumentRepository instrumentRepository;

    public TradeQueryService(
            AccountService accountService,
            TradeRepository tradeRepository,
            InstrumentRepository instrumentRepository) {
        this.accountService = accountService;
        this.tradeRepository = tradeRepository;
        this.instrumentRepository = instrumentRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TradeResponse> list(Long userId, Pageable pageable) {
        Long accountId = accountService.getAccountForUser(userId).getId();
        Page<Trade> trades = tradeRepository.findByAccountId(accountId, pageable);
        Map<Long, String> symbols = instrumentRepository.findAllById(trades.map(Trade::getInstrumentId).toSet())
                .stream()
                .collect(Collectors.toMap(Instrument::getId, Instrument::getSymbol));

        return PageResponse.from(trades, trade -> {
            OrderSide side = trade.getBuyerAccountId().equals(accountId) ? OrderSide.BUY : OrderSide.SELL;
            return new TradeResponse(
                    trade.getId(),
                    symbols.get(trade.getInstrumentId()),
                    side,
                    trade.getPrice(),
                    trade.getQuantity(),
                    Money.notional(trade.getQuantity(), trade.getPrice()),
                    side == OrderSide.BUY ? trade.getBuyOrderId() : trade.getSellOrderId(),
                    side == trade.getAggressorSide() ? TradeResponse.Liquidity.TAKER : TradeResponse.Liquidity.MAKER,
                    trade.getCreatedAt());
        });
    }
}
