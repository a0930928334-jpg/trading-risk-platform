package com.shuhongyi.trading.trade;

import com.shuhongyi.trading.auth.CurrentUserId;
import com.shuhongyi.trading.common.web.PageResponse;
import com.shuhongyi.trading.common.web.Pagination;
import com.shuhongyi.trading.trade.dto.TradeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Trades", description = "Executions involving the caller.")
@RequestMapping("/api/trades")
public class TradeController {

    private final TradeQueryService tradeQueryService;

    public TradeController(TradeQueryService tradeQueryService) {
        this.tradeQueryService = tradeQueryService;
    }

    @Operation(summary = "List my trades", description = "Newest first, from the caller's side, with MAKER/TAKER liquidity.")
    @ApiResponse(responseCode = "200", description = "Page of trades")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @GetMapping
    public PageResponse<TradeResponse> list(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + Pagination.DEFAULT_SIZE) int size) {
        return tradeQueryService.list(userId, Pagination.newestFirst(page, size));
    }
}
