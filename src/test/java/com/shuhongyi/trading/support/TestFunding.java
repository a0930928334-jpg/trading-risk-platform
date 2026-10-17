package com.shuhongyi.trading.support;

import com.shuhongyi.trading.account.CashService;
import com.shuhongyi.trading.common.money.Money;
import com.shuhongyi.trading.position.PositionService;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gives test accounts cash and shares directly through the domain services (there is no share deposit API).
 */
public class TestFunding {

    private final TransactionTemplate transactionTemplate;
    private final CashService cashService;
    private final PositionService positionService;

    public TestFunding(TransactionTemplate transactionTemplate, CashService cashService, PositionService positionService) {
        this.transactionTemplate = transactionTemplate;
        this.cashService = cashService;
        this.positionService = positionService;
    }

    public void depositCash(Long accountId, String amount) {
        transactionTemplate.executeWithoutResult(status ->
                cashService.deposit(cashService.lock(accountId), Money.of(amount)));
    }

    public void grantShares(Long accountId, Long instrumentId, long shares) {
        transactionTemplate.executeWithoutResult(status ->
                positionService.credit(positionService.lock(accountId, instrumentId), shares));
    }
}
