package com.shuhongyi.trading.position;

import com.shuhongyi.trading.auth.CurrentUserId;
import com.shuhongyi.trading.common.web.PageResponse;
import com.shuhongyi.trading.common.web.Pagination;
import com.shuhongyi.trading.position.dto.PositionResponse;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/positions")
public class PositionController {

    private final PositionQueryService positionQueryService;

    public PositionController(PositionQueryService positionQueryService) {
        this.positionQueryService = positionQueryService;
    }

    @GetMapping
    public PageResponse<PositionResponse> getPositions(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + Pagination.DEFAULT_SIZE) int size) {
        return positionQueryService.getPositions(userId, Pagination.of(page, size, Sort.by("instrumentId")));
    }
}
