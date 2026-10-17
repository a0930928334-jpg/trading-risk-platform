package com.shuhongyi.trading.account;

public enum CashLedgerEntryType {
    /** External cash paid in: balance increases. */
    DEPOSIT,
    /** Funds earmarked for an open buy order: reserved increases, balance unchanged. */
    RESERVE,
    /** Unused reservation returned (cancel, better fill price, rejection): reserved decreases. */
    RELEASE,
    /** Buy fill paid from reserved funds: balance and reserved both decrease. */
    TRADE_DEBIT,
    /** Sell fill proceeds: balance increases. */
    TRADE_CREDIT
}
