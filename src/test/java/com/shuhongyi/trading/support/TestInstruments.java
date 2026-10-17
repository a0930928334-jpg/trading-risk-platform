package com.shuhongyi.trading.support;

import com.shuhongyi.trading.common.money.Money;
import com.shuhongyi.trading.instrument.Instrument;
import com.shuhongyi.trading.instrument.InstrumentRepository;
import com.shuhongyi.trading.instrument.InstrumentStatus;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

/**
 * Creates a fresh instrument per test so tests that trade never share an order book.
 */
public final class TestInstruments {

    private TestInstruments() {
    }

    public static Instrument create(InstrumentRepository repository, String referencePrice) {
        String symbol = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        return repository.saveAndFlush(Instrument.builder()
                .symbol(symbol)
                .name("Test instrument " + symbol)
                .status(InstrumentStatus.ACTIVE)
                .currency(Money.CURRENCY)
                .referencePrice(Money.of(new BigDecimal(referencePrice)))
                .build());
    }
}
