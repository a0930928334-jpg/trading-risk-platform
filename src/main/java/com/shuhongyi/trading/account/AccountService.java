package com.shuhongyi.trading.account;

import com.shuhongyi.trading.account.dto.AccountResponse;
import com.shuhongyi.trading.account.dto.DepositResponse;
import com.shuhongyi.trading.account.dto.LedgerEntryResponse;
import com.shuhongyi.trading.account.exception.AccountNotActiveException;
import com.shuhongyi.trading.account.exception.AccountNotFoundException;
import com.shuhongyi.trading.common.money.Money;
import com.shuhongyi.trading.common.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final CashBalanceRepository cashBalanceRepository;
    private final CashLedgerEntryRepository ledgerRepository;
    private final CashService cashService;

    public AccountService(
            AccountRepository accountRepository,
            CashBalanceRepository cashBalanceRepository,
            CashLedgerEntryRepository ledgerRepository,
            CashService cashService) {
        this.accountRepository = accountRepository;
        this.cashBalanceRepository = cashBalanceRepository;
        this.ledgerRepository = ledgerRepository;
        this.cashService = cashService;
    }

    @Transactional(readOnly = true)
    public Account getAccountForUser(Long userId) {
        return accountRepository.findFirstByUser_IdOrderByIdAsc(userId)
                .orElseThrow(() -> AccountNotFoundException.forUser(userId));
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountSummary(Long userId) {
        Account account = getAccountForUser(userId);
        CashBalance cash = cashBalanceRepository.findByAccount_Id(account.getId())
                .orElseThrow(() -> AccountNotFoundException.forAccount(account.getId()));
        return toResponse(account, cash);
    }

    @Transactional
    public DepositResponse deposit(Long userId, BigDecimal amount) {
        Account account = getAccountForUser(userId);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(account.getId());
        }

        CashBalance cash = cashService.lock(account.getId());
        CashLedgerEntry entry = cashService.deposit(cash, Money.of(amount));

        return new DepositResponse(LedgerEntryResponse.from(entry), toResponse(account, cash));
    }

    @Transactional(readOnly = true)
    public PageResponse<LedgerEntryResponse> getLedger(Long userId, Pageable pageable) {
        Account account = getAccountForUser(userId);
        return PageResponse.from(ledgerRepository.findByAccountId(account.getId(), pageable), LedgerEntryResponse::from);
    }

    private static AccountResponse toResponse(Account account, CashBalance cash) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getStatus(),
                account.getCurrency(),
                cash.getBalance(),
                cash.getReserved(),
                cash.getAvailable());
    }
}
