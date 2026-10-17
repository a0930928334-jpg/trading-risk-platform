package com.shuhongyi.trading.account;

import com.shuhongyi.trading.account.exception.AccountNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The only component that changes cash balances. Every change is paired with a ledger entry,
 * so the ledger always explains the current balance and reservation.
 * All methods require an existing transaction: callers own the transaction boundary and the row locks.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class CashService {

    private final CashBalanceRepository cashBalanceRepository;
    private final CashLedgerEntryRepository ledgerRepository;

    public CashService(CashBalanceRepository cashBalanceRepository, CashLedgerEntryRepository ledgerRepository) {
        this.cashBalanceRepository = cashBalanceRepository;
        this.ledgerRepository = ledgerRepository;
    }

    public CashBalance lock(Long accountId) {
        return cashBalanceRepository.findByAccountIdForUpdate(accountId)
                .orElseThrow(() -> AccountNotFoundException.forAccount(accountId));
    }

    /**
     * Locks several balances in ascending account ID order. Every code path that locks more than one
     * balance goes through here, so two transactions can never wait on each other in a cycle.
     */
    public Map<Long, CashBalance> lockInOrder(Collection<Long> accountIds) {
        Map<Long, CashBalance> locked = new LinkedHashMap<>();
        accountIds.stream().distinct().sorted().forEach(id -> locked.put(id, lock(id)));
        return locked;
    }

    public CashLedgerEntry deposit(CashBalance cash, BigDecimal amount) {
        cash.deposit(amount);
        return record(cash, CashLedgerEntryType.DEPOSIT, amount, null, null);
    }

    public CashLedgerEntry reserve(CashBalance cash, BigDecimal amount, Long orderId) {
        cash.reserve(amount);
        return record(cash, CashLedgerEntryType.RESERVE, amount, orderId, null);
    }

    public CashLedgerEntry release(CashBalance cash, BigDecimal amount, Long orderId) {
        cash.release(amount);
        return record(cash, CashLedgerEntryType.RELEASE, amount, orderId, null);
    }

    public CashLedgerEntry debitReserved(CashBalance cash, BigDecimal amount, Long orderId, Long tradeId) {
        cash.debitReserved(amount);
        return record(cash, CashLedgerEntryType.TRADE_DEBIT, amount, orderId, tradeId);
    }

    public CashLedgerEntry credit(CashBalance cash, BigDecimal amount, Long orderId, Long tradeId) {
        cash.credit(amount);
        return record(cash, CashLedgerEntryType.TRADE_CREDIT, amount, orderId, tradeId);
    }

    private CashLedgerEntry record(
            CashBalance cash, CashLedgerEntryType type, BigDecimal amount, Long orderId, Long tradeId) {
        return ledgerRepository.save(CashLedgerEntry.builder()
                .accountId(cash.getAccount().getId())
                .entryType(type)
                .currency(cash.getCurrency())
                .amount(amount)
                .balanceAfter(cash.getBalance())
                .reservedAfter(cash.getReserved())
                .orderId(orderId)
                .tradeId(tradeId)
                .build());
    }
}
