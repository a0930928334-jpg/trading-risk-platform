package com.shuhongyi.trading.risk.rules;

import com.shuhongyi.trading.account.AccountStatus;
import com.shuhongyi.trading.risk.RiskDecision;
import org.junit.jupiter.api.Test;

import static com.shuhongyi.trading.risk.RiskContexts.order;
import static org.assertj.core.api.Assertions.assertThat;

class AccountStatusRuleTest {

    private final AccountStatusRule rule = new AccountStatusRule();

    @Test
    void acceptsActiveAccount() {
        assertThat(rule.evaluate(order().build()).accepted()).isTrue();
    }

    @Test
    void rejectsClosedAccount() {
        RiskDecision decision = rule.evaluate(order().accountStatus(AccountStatus.CLOSED).build());

        assertThat(decision.rejected()).isTrue();
        assertThat(decision.code()).isEqualTo(AccountStatusRule.CODE);
        assertThat(decision.reason()).contains("CLOSED");
    }
}
