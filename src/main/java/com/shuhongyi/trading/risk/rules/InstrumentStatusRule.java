package com.shuhongyi.trading.risk.rules;

import com.shuhongyi.trading.instrument.InstrumentStatus;
import com.shuhongyi.trading.risk.RiskContext;
import com.shuhongyi.trading.risk.RiskDecision;
import com.shuhongyi.trading.risk.RiskRule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class InstrumentStatusRule implements RiskRule {

    public static final String CODE = "INSTRUMENT_NOT_ACTIVE";

    @Override
    public RiskDecision evaluate(RiskContext context) {
        if (context.instrument().getStatus() != InstrumentStatus.ACTIVE) {
            return RiskDecision.reject(CODE, "Instrument %s is %s".formatted(
                    context.instrument().getSymbol(), context.instrument().getStatus()));
        }
        return RiskDecision.accept();
    }
}
