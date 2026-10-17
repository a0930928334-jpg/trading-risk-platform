package com.shuhongyi.trading.instrument;

import com.shuhongyi.trading.common.web.PageResponse;
import com.shuhongyi.trading.instrument.dto.InstrumentResponse;
import com.shuhongyi.trading.instrument.exception.InstrumentNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class InstrumentService {

    private final InstrumentRepository instrumentRepository;

    public InstrumentService(InstrumentRepository instrumentRepository) {
        this.instrumentRepository = instrumentRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<InstrumentResponse> list(Pageable pageable) {
        return PageResponse.from(instrumentRepository.findAll(pageable), InstrumentResponse::from);
    }

    @Transactional(readOnly = true)
    public Instrument getBySymbol(String symbol) {
        String normalised = normaliseSymbol(symbol);
        return instrumentRepository.findBySymbol(normalised)
                .orElseThrow(() -> new InstrumentNotFoundException(normalised));
    }

    public static String normaliseSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
