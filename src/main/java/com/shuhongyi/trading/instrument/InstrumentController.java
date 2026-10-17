package com.shuhongyi.trading.instrument;

import com.shuhongyi.trading.common.web.PageResponse;
import com.shuhongyi.trading.common.web.Pagination;
import com.shuhongyi.trading.instrument.dto.InstrumentResponse;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {

    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @GetMapping
    public PageResponse<InstrumentResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + Pagination.DEFAULT_SIZE) int size) {
        return instrumentService.list(Pagination.of(page, size, Sort.by("symbol")));
    }
}
