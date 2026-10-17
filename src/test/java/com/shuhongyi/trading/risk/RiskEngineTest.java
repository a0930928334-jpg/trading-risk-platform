package com.shuhongyi.trading.risk;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.shuhongyi.trading.risk.RiskContexts.order;
import static org.assertj.core.api.Assertions.assertThat;

class RiskEngineTest {

    @Test
    void acceptsWhenEveryRuleAccepts() {
        RiskEngine engine = new RiskEngine(List.of(context -> RiskDecision.accept(), context -> RiskDecision.accept()));

        assertThat(engine.evaluate(order().build()).accepted()).isTrue();
    }

    @Test
    void stopsAtTheFirstRejectionInOrder() {
        List<String> evaluated = new ArrayList<>();
        RiskRule first = context -> {
            evaluated.add("first");
            return RiskDecision.accept();
        };
        RiskRule second = context -> {
            evaluated.add("second");
            return RiskDecision.reject("SECOND", "second rule failed");
        };
        RiskRule third = context -> {
            evaluated.add("third");
            return RiskDecision.reject("THIRD", "never reached");
        };

        RiskDecision decision = new RiskEngine(List.of(first, second, third)).evaluate(order().build());

        assertThat(decision.code()).isEqualTo("SECOND");
        assertThat(evaluated).containsExactly("first", "second");
    }

    @Test
    void emptyChainAcceptsEverything() {
        assertThat(new RiskEngine(List.of()).evaluate(order().build()).accepted()).isTrue();
    }
}
