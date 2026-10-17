package com.shuhongyi.trading.risk;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Chain of responsibility over all {@link RiskRule} beans, in {@code @Order} sequence.
 */
@Component
public class RiskEngine {

    private final List<RiskRule> rules;

    public RiskEngine(List<RiskRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public RiskDecision evaluate(RiskContext context) {
        for (RiskRule rule : rules) {
            RiskDecision decision = rule.evaluate(context);
            if (decision.rejected()) {
                return decision;
            }
        }
        return RiskDecision.accept();
    }

    public List<RiskRule> rules() {
        return rules;
    }
}
