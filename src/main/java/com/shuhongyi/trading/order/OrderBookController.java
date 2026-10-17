package com.shuhongyi.trading.order;

import com.shuhongyi.trading.instrument.Instrument;
import com.shuhongyi.trading.instrument.InstrumentService;
import com.shuhongyi.trading.matching.OrderBook;
import com.shuhongyi.trading.order.dto.OrderBookResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/instruments/{symbol}/book")
public class OrderBookController {

    private static final int MAX_DEPTH = 50;

    private final InstrumentService instrumentService;
    private final OrderBookRegistry orderBooks;
    private final InstrumentLockManager lockManager;

    public OrderBookController(
            InstrumentService instrumentService,
            OrderBookRegistry orderBooks,
            InstrumentLockManager lockManager) {
        this.instrumentService = instrumentService;
        this.orderBooks = orderBooks;
        this.lockManager = lockManager;
    }

    @GetMapping
    public OrderBookResponse getBook(@PathVariable String symbol, @RequestParam(defaultValue = "10") int depth) {
        Instrument instrument = instrumentService.getBySymbol(symbol);
        int levels = Math.min(Math.max(depth, 1), MAX_DEPTH);
        // The book is not thread-safe, so even a read takes the instrument lock.
        return lockManager.withLock(instrument.getId(), () -> {
            OrderBook book = orderBooks.book(instrument.getId());
            Instrument current = instrumentService.getBySymbol(symbol);
            return new OrderBookResponse(
                    current.getSymbol(),
                    current.getLastTradePrice(),
                    current.getReferencePrice(),
                    book.depth(OrderSide.BUY, levels),
                    book.depth(OrderSide.SELL, levels));
        });
    }
}
