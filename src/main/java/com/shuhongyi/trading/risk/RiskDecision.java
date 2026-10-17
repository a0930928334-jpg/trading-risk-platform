package com.shuhongyi.trading.risk;

/**
 * Outcome of a risk rule. A rejection carries a stable machine-readable code and a human-readable reason;
 * both are stored on the rejected order.
 */
public record RiskDecision(boolean accepted, String code, String reason) {

    private static final RiskDecision ACCEPTED = new RiskDecision(true, null, null);

    public static RiskDecision accept() {
        return ACCEPTED;
    }

    public static RiskDecision reject(String code, String reason) {
        return new RiskDecision(false, code, reason);
    }

    public boolean rejected() {
        return !accepted;
    }
}
