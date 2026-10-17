package com.shuhongyi.trading.instrument.exception;

import com.shuhongyi.trading.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class InstrumentNotFoundException extends ApiException {

    public InstrumentNotFoundException(String symbol) {
        super(HttpStatus.NOT_FOUND, "instrument-not-found", "Instrument not found: " + symbol);
    }
}
