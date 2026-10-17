package com.shuhongyi.trading.risk;

/**
 * One pre-trade check. Rules are Spring beans; {@link RiskEngine} runs them in {@code @Order} sequence
 * and stops at the first rejection. Adding a rule means adding one class: nothing else changes.
 */
public interface RiskRule {

    RiskDecision evaluate(RiskContext context);
}
