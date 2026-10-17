package com.shuhongyi.trading.instrument.dto;

import com.shuhongyi.trading.instrument.Instrument;
import com.shuhongyi.trading.instrument.InstrumentStatus;

import java.math.BigDecimal;

public record InstrumentResponse(
        String symbol,
        String name,
        InstrumentStatus status,
        String currency,
        BigDecimal referencePrice,
        BigDecimal lastTradePrice
) {

    public static InstrumentResponse from(Instrument instrument) {
        return new InstrumentResponse(
                instrument.getSymbol(),
                instrument.getName(),
                instrument.getStatus(),
                instrument.getCurrency(),
                instrument.getReferencePrice(),
                instrument.getLastTradePrice());
    }
}
