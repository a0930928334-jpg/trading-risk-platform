package com.shuhongyi.trading.risk.rules;

import com.shuhongyi.trading.instrument.InstrumentStatus;
import com.shuhongyi.trading.risk.RiskDecision;
import org.junit.jupiter.api.Test;

import static com.shuhongyi.trading.risk.RiskContexts.order;
import static org.assertj.core.api.Assertions.assertThat;

class InstrumentStatusRuleTest {

    private final InstrumentStatusRule rule = new InstrumentStatusRule();

    @Test
    void acceptsActiveInstrument() {
        assertThat(rule.evaluate(order().build()).accepted()).isTrue();
    }

    @Test
    void rejectsHaltedInstrument() {
        RiskDecision decision = rule.evaluate(order().instrumentStatus(InstrumentStatus.HALTED).build());

        assertThat(decision.rejected()).isTrue();
        assertThat(decision.code()).isEqualTo(InstrumentStatusRule.CODE);
        assertThat(decision.reason()).contains("HALTED");
    }
}
