package com.shuhongyi.trading.trade;

import com.shuhongyi.trading.auth.CurrentUserId;
import com.shuhongyi.trading.common.web.PageResponse;
import com.shuhongyi.trading.common.web.Pagination;
import com.shuhongyi.trading.trade.dto.TradeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trades")
public class TradeController {

    private final TradeQueryService tradeQueryService;

    public TradeController(TradeQueryService tradeQueryService) {
        this.tradeQueryService = tradeQueryService;
    }

    @GetMapping
    public PageResponse<TradeResponse> list(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + Pagination.DEFAULT_SIZE) int size) {
        return tradeQueryService.list(userId, Pagination.newestFirst(page, size));
    }
}
