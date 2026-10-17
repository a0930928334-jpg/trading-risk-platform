package com.shuhongyi.trading.order.exception;

import com.shuhongyi.trading.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class InstrumentBusyException extends ApiException {

    public InstrumentBusyException(Long instrumentId) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "instrument-busy",
                "Instrument %d is busy; please retry".formatted(instrumentId));
    }
}
