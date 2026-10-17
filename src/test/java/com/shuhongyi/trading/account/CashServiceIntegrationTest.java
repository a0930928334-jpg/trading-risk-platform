package com.shuhongyi.trading.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuhongyi.trading.common.money.Money;
import com.shuhongyi.trading.support.IntegrationTest;
import com.shuhongyi.trading.support.TestUser;
import com.shuhongyi.trading.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class CashServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CashService cashService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private CashBalanceRepository cashBalanceRepository;

    @Autowired
    private CashLedgerEntryRepository ledgerRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void everyMovementIsLedgeredAndLedgerReplaysToCurrentState() throws Exception {
        TestUser user = TestUsers.create(mockMvc, objectMapper);
        Long accountId = user.accountId();

        transactionTemplate.executeWithoutResult(status -> {
            CashBalance cash = cashService.lock(accountId);
            cashService.deposit(cash, Money.of("1000.0000"));
            cashService.reserve(cash, Money.of("400.0000"), null);
            cashService.debitReserved(cash, Money.of("350.0000"), null, null);
            cashService.release(cash, Money.of("50.0000"), null);
            cashService.credit(cash, Money.of("120.0000"), null, null);
        });

        List<CashLedgerEntry> entries = ledgerRepository.findByAccountIdOrderByIdAsc(accountId);
        assertThat(entries).extracting(CashLedgerEntry::getEntryType).containsExactly(
                CashLedgerEntryType.DEPOSIT,
                CashLedgerEntryType.RESERVE,
                CashLedgerEntryType.TRADE_DEBIT,
                CashLedgerEntryType.RELEASE,
                CashLedgerEntryType.TRADE_CREDIT);

        BigDecimal balance = Money.ZERO;
        BigDecimal reserved = Money.ZERO;
        for (CashLedgerEntry entry : entries) {
            switch (entry.getEntryType()) {
                case DEPOSIT, TRADE_CREDIT -> balance = balance.add(entry.getAmount());
                case RESERVE -> reserved = reserved.add(entry.getAmount());
                case RELEASE -> reserved = reserved.subtract(entry.getAmount());
                case TRADE_DEBIT -> {
                    balance = balance.subtract(entry.getAmount());
                    reserved = reserved.subtract(entry.getAmount());
                }
            }
            assertThat(entry.getBalanceAfter()).isEqualByComparingTo(balance);
            assertThat(entry.getReservedAfter()).isEqualByComparingTo(reserved);
        }

        CashBalance stored = cashBalanceRepository.findByAccount_Id(accountId).orElseThrow();
        assertThat(stored.getBalance()).isEqualByComparingTo("770.0000");
        assertThat(stored.getReserved()).isEqualByComparingTo("0.0000");
    }

    @Test
    void cashServiceRefusesToRunOutsideATransaction() throws Exception {
        TestUser user = TestUsers.create(mockMvc, objectMapper);

        assertThatThrownBy(() -> cashService.lock(user.accountId()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void lockInOrderLocksAccountsInAscendingIdOrder() throws Exception {
        TestUser first = TestUsers.create(mockMvc, objectMapper);
        TestUser second = TestUsers.create(mockMvc, objectMapper);

        Map<Long, CashBalance> locked = transactionTemplate.execute(status ->
                cashService.lockInOrder(List.of(second.accountId(), first.accountId(), second.accountId())));

        assertThat(locked.keySet()).containsExactly(first.accountId(), second.accountId());
    }

    @Test
    void databaseRejectsReservationAboveBalance() throws Exception {
        TestUser user = TestUsers.create(mockMvc, objectMapper);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE cash_balances SET reserved = balance + 1 WHERE account_id = ?", user.accountId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentDepositsAreSerialisedByRowLock() throws Exception {
        TestUser user = TestUsers.create(mockMvc, objectMapper);
        int threads = 16;
        int depositsPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Void> task = () -> {
                    start.await();
                    for (int j = 0; j < depositsPerThread; j++) {
                        accountService.deposit(user.userId(), new BigDecimal("10.0000"));
                    }
                    return null;
                };
                futures.add(executor.submit(task));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdown();
        }

        CashBalance stored = cashBalanceRepository.findByAccount_Id(user.accountId()).orElseThrow();
        assertThat(stored.getBalance()).isEqualByComparingTo("800.0000");
        List<CashLedgerEntry> entries = ledgerRepository.findByAccountIdOrderByIdAsc(user.accountId());
        assertThat(entries).hasSize(threads * depositsPerThread);
        // With the row lock, each entry's snapshot is exactly 10 more than the previous one.
        for (int i = 0; i < entries.size(); i++) {
            assertThat(entries.get(i).getBalanceAfter())
                    .isEqualByComparingTo(new BigDecimal(10 * (i + 1)));
        }
    }
}
