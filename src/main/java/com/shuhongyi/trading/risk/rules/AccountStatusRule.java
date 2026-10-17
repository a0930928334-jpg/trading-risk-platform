package com.shuhongyi.trading.risk.rules;

import com.shuhongyi.trading.account.AccountStatus;
import com.shuhongyi.trading.risk.RiskContext;
import com.shuhongyi.trading.risk.RiskDecision;
import com.shuhongyi.trading.risk.RiskRule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class AccountStatusRule implements RiskRule {

    public static final String CODE = "ACCOUNT_NOT_ACTIVE";

    @Override
    public RiskDecision evaluate(RiskContext context) {
        if (context.account().getStatus() != AccountStatus.ACTIVE) {
            return RiskDecision.reject(CODE, "Account is %s".formatted(context.account().getStatus()));
        }
        return RiskDecision.accept();
    }
}
