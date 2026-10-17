package com.shuhongyi.trading.instrument;

import com.shuhongyi.trading.common.web.PageResponse;
import com.shuhongyi.trading.common.web.Pagination;
import com.shuhongyi.trading.instrument.dto.InstrumentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Instruments", description = "Tradable symbols with simulated reference prices.")
@RequestMapping("/api/instruments")
public class InstrumentController {

    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @Operation(summary = "List instruments")
    @ApiResponse(responseCode = "200", description = "Page of instruments")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @GetMapping
    public PageResponse<InstrumentResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + Pagination.DEFAULT_SIZE) int size) {
        return instrumentService.list(Pagination.of(page, size, Sort.by("symbol")));
    }
}
