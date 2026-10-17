package com.shuhongyi.trading.risk.rules;

import com.shuhongyi.trading.order.OrderSide;
import com.shuhongyi.trading.risk.RiskDecision;
import com.shuhongyi.trading.risk.RiskProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.shuhongyi.trading.risk.RiskContexts.order;
import static org.assertj.core.api.Assertions.assertThat;

class MaxOrderNotionalRuleTest {

    private final MaxOrderNotionalRule rule = new MaxOrderNotionalRule(new RiskProperties(null, null));

    @Test
    void defaultLimitIsOneHundredThousand() {
        assertThat(new RiskProperties(null, null).maxOrderNotional()).isEqualByComparingTo("100000");
    }

    @Test
    void acceptsOrderExactlyAtTheLimit() {
        assertThat(rule.evaluate(order().price("100.0000").quantity(1_000).build()).accepted()).isTrue();
    }

    @Test
    void rejectsOrderAboveTheLimit() {
        RiskDecision decision = rule.evaluate(order().price("100.0001").quantity(1_000).build());

        assertThat(decision.rejected()).isTrue();
        assertThat(decision.code()).isEqualTo(MaxOrderNotionalRule.CODE);
        assertThat(decision.reason()).contains("100000.1000");
    }

    @Test
    void appliesToSellOrdersToo() {
        RiskDecision decision = rule.evaluate(order().side(OrderSide.SELL).price("200.0000").quantity(501).build());

        assertThat(decision.rejected()).isTrue();
    }

    @Test
    void limitIsConfigurable() {
        MaxOrderNotionalRule strict = new MaxOrderNotionalRule(new RiskProperties(new BigDecimal("500"), null));

        assertThat(strict.evaluate(order().price("100.0000").quantity(6).build()).rejected()).isTrue();
        assertThat(strict.evaluate(order().price("100.0000").quantity(5).build()).accepted()).isTrue();
    }
}
