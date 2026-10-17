package com.shuhongyi.trading.concurrency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuhongyi.trading.account.CashBalance;
import com.shuhongyi.trading.account.CashBalanceRepository;
import com.shuhongyi.trading.account.CashLedgerEntry;
import com.shuhongyi.trading.account.CashLedgerEntryRepository;
import com.shuhongyi.trading.common.error.ApiException;
import com.shuhongyi.trading.common.money.Money;
import com.shuhongyi.trading.instrument.Instrument;
import com.shuhongyi.trading.instrument.InstrumentRepository;
import com.shuhongyi.trading.matching.OrderBook;
import com.shuhongyi.trading.matching.PriceLevelView;
import com.shuhongyi.trading.order.Order;
import com.shuhongyi.trading.order.OrderBookRegistry;
import com.shuhongyi.trading.order.OrderCommandService;
import com.shuhongyi.trading.order.OrderRepository;
import com.shuhongyi.trading.order.OrderSide;
import com.shuhongyi.trading.order.OrderStatus;
import com.shuhongyi.trading.order.dto.PlaceOrderRequest;
import com.shuhongyi.trading.position.Position;
import com.shuhongyi.trading.position.PositionRepository;
import com.shuhongyi.trading.support.IntegrationTest;
import com.shuhongyi.trading.support.TestFunding;
import com.shuhongyi.trading.support.TestInstruments;
import com.shuhongyi.trading.support.TestUser;
import com.shuhongyi.trading.support.TestUsers;
import com.shuhongyi.trading.trade.Trade;
import com.shuhongyi.trading.trade.TradeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Many threads place and cancel random orders for a shared set of accounts on two instruments at once.
 * Afterwards nothing may have been created or destroyed: total cash and total shares are conserved,
 * nothing is negative, every reservation matches the open orders, the ledger replays to each balance,
 * and each in-memory book equals a fresh rebuild from the database.
 */
@IntegrationTest
class ConcurrentTradingConservationTest {

    private static final int ACCOUNTS = 8;
    private static final int THREADS = 12;
    private static final int COMMANDS_PER_THREAD = 60;
    private static final BigDecimal CASH_PER_ACCOUNT = Money.of("50000.0000");
    private static final long SHARES_PER_ACCOUNT = 500;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestFunding testFunding;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private OrderCommandService orderCommandService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private CashBalanceRepository cashBalanceRepository;

    @Autowired
    private CashLedgerEntryRepository ledgerRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private OrderBookRegistry orderBooks;

    @Test
    void concurrentTradingConservesCashAndSharesAndKeepsEveryInvariant() throws Exception {
        List<Instrument> instruments = List.of(
                TestInstruments.create(instrumentRepository, "100.0000"),
                TestInstruments.create(instrumentRepository, "40.0000"));
        List<TestUser> users = new ArrayList<>();
        for (int i = 0; i < ACCOUNTS; i++) {
            TestUser user = TestUsers.create(mockMvc, objectMapper);
            testFunding.depositCash(user.accountId(), CASH_PER_ACCOUNT.toPlainString());
            for (Instrument instrument : instruments) {
                testFunding.grantShares(user.accountId(), instrument.getId(), SHARES_PER_ACCOUNT);
            }
            users.add(user);
        }

        runConcurrently(users, instruments);

        List<Long> accountIds = users.stream().map(TestUser::accountId).toList();
        assertTradesAndCancellationsHappened(accountIds, instruments);
        assertCashConservedAndNonNegative(accountIds);
        assertSharesConservedAndNonNegative(accountIds, instruments);
        assertReservationsMatchOpenOrders(accountIds, instruments);
        assertLedgerReplaysToBalances(accountIds);
        assertBooksMatchDatabaseAndAreNotCrossed(instruments);
    }

    private void runConcurrently(List<TestUser> users, List<Instrument> instruments) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < THREADS; t++) {
                Random random = new Random(1_000L + t);
                Callable<Void> worker = () -> {
                    start.await();
                    for (int i = 0; i < COMMANDS_PER_THREAD; i++) {
                        TestUser user = users.get(random.nextInt(users.size()));
                        Instrument instrument = instruments.get(random.nextInt(instruments.size()));
                        try {
                            if (random.nextInt(5) == 0) {
                                cancelSomeOpenOrder(user, random);
                            } else {
                                placeRandomOrder(user, instrument, random);
                            }
                        } catch (ApiException expected) {
                            // For example cancelling an order another thread has just filled.
                        }
                    }
                    return null;
                };
                futures.add(executor.submit(worker));
            }
            start.countDown();
            for (Future<Void> future : futures) {
                // get() rethrows any unexpected exception (deadlock, constraint violation, ...) and fails the test.
                future.get(2, TimeUnit.MINUTES);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private void placeRandomOrder(TestUser user, Instrument instrument, Random random) {
        OrderSide side = random.nextBoolean() ? OrderSide.BUY : OrderSide.SELL;
        // Prices within +/-5% of the reference so orders cross often and stay inside the 10% band.
        BigDecimal reference = instrument.getReferencePrice();
        BigDecimal offset = reference.multiply(BigDecimal.valueOf(random.nextInt(1_001) - 500, 4));
        BigDecimal price = reference.add(offset).setScale(2, RoundingMode.HALF_UP);
        long quantity = 1 + random.nextInt(25);
        orderCommandService.placeForAccount(user.accountId(), instrument.getId(),
                new PlaceOrderRequest(instrument.getSymbol(), side, null, null, price, quantity));
    }

    private void cancelSomeOpenOrder(TestUser user, Random random) {
        List<Order> open = orderRepository.findAll().stream()
                .filter(order -> order.getAccountId().equals(user.accountId()) && order.isOpen())
                .toList();
        if (!open.isEmpty()) {
            orderCommandService.cancel(user.userId(), open.get(random.nextInt(open.size())).getId());
        }
    }

    private void assertTradesAndCancellationsHappened(List<Long> accountIds, List<Instrument> instruments) {
        assertThat(orderRepository.findAll()).anySatisfy(order -> {
            assertThat(accountIds).contains(order.getAccountId());
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        });
        for (Instrument instrument : instruments) {
            List<Trade> trades = tradeRepository.findByInstrumentIdOrderByIdAsc(instrument.getId());
            assertThat(trades).as("trades on %s", instrument.getSymbol()).isNotEmpty();
            assertThat(trades).allSatisfy(trade ->
                    assertThat(trade.getBuyerAccountId()).isNotEqualTo(trade.getSellerAccountId()));
        }
    }

    private void assertCashConservedAndNonNegative(List<Long> accountIds) {
        BigDecimal total = Money.ZERO;
        for (Long accountId : accountIds) {
            CashBalance cash = cashBalanceRepository.findByAccount_Id(accountId).orElseThrow();
            assertThat(cash.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(cash.getReserved()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(cash.getAvailable()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            total = total.add(cash.getBalance());
        }
        assertThat(total).isEqualByComparingTo(CASH_PER_ACCOUNT.multiply(BigDecimal.valueOf(ACCOUNTS)));
    }

    private void assertSharesConservedAndNonNegative(List<Long> accountIds, List<Instrument> instruments) {
        for (Instrument instrument : instruments) {
            long total = 0;
            for (Long accountId : accountIds) {
                Position position = positionRepository.findByAccountIdAndInstrumentId(accountId, instrument.getId())
                        .orElseThrow();
                assertThat(position.getQuantity()).isNotNegative();
                assertThat(position.getReservedQuantity()).isNotNegative();
                assertThat(position.getAvailableQuantity()).isNotNegative();
                total += position.getQuantity();
            }
            assertThat(total).as("shares of %s", instrument.getSymbol()).isEqualTo(SHARES_PER_ACCOUNT * ACCOUNTS);
            assertThat(positionRepository.sumQuantityByInstrumentId(instrument.getId())).isEqualTo(total);
        }
    }

    private void assertReservationsMatchOpenOrders(List<Long> accountIds, List<Instrument> instruments) {
        List<Order> openOrders = orderRepository.findAll().stream()
                .filter(order -> accountIds.contains(order.getAccountId())
                        && EnumSet.of(OrderStatus.NEW, OrderStatus.PARTIALLY_FILLED).contains(order.getStatus()))
                .toList();
        for (Long accountId : accountIds) {
            BigDecimal expectedCash = openOrders.stream()
                    .filter(order -> order.getAccountId().equals(accountId) && order.getSide() == OrderSide.BUY)
                    .map(Order::getRemainingNotional)
                    .reduce(Money.ZERO, BigDecimal::add);
            assertThat(cashBalanceRepository.findByAccount_Id(accountId).orElseThrow().getReserved())
                    .as("reserved cash of account %d", accountId)
                    .isEqualByComparingTo(expectedCash);

            for (Instrument instrument : instruments) {
                long expectedShares = openOrders.stream()
                        .filter(order -> order.getAccountId().equals(accountId)
                                && order.getInstrumentId().equals(instrument.getId())
                                && order.getSide() == OrderSide.SELL)
                        .mapToLong(Order::getRemainingQuantity)
                        .sum();
                assertThat(positionRepository.findByAccountIdAndInstrumentId(accountId, instrument.getId())
                        .orElseThrow().getReservedQuantity())
                        .as("reserved shares of account %d", accountId)
                        .isEqualTo(expectedShares);
            }
        }
    }

    private void assertLedgerReplaysToBalances(List<Long> accountIds) {
        for (Long accountId : accountIds) {
            BigDecimal balance = Money.ZERO;
            BigDecimal reserved = Money.ZERO;
            for (CashLedgerEntry entry : ledgerRepository.findByAccountIdOrderByIdAsc(accountId)) {
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
            CashBalance cash = cashBalanceRepository.findByAccount_Id(accountId).orElseThrow();
            assertThat(cash.getBalance()).isEqualByComparingTo(balance);
            assertThat(cash.getReserved()).isEqualByComparingTo(reserved);
        }
    }

    private void assertBooksMatchDatabaseAndAreNotCrossed(List<Instrument> instruments) {
        for (Instrument instrument : instruments) {
            OrderBook live = orderBooks.book(instrument.getId());
            List<PriceLevelView> liveBids = live.depth(OrderSide.BUY, Integer.MAX_VALUE);
            List<PriceLevelView> liveAsks = live.depth(OrderSide.SELL, Integer.MAX_VALUE);

            OrderBook rebuilt = orderBooks.rebuild(instrument.getId());
            assertThat(liveBids).isEqualTo(rebuilt.depth(OrderSide.BUY, Integer.MAX_VALUE));
            assertThat(liveAsks).isEqualTo(rebuilt.depth(OrderSide.SELL, Integer.MAX_VALUE));

            if (rebuilt.bestBid().isPresent() && rebuilt.bestAsk().isPresent()) {
                assertThat(rebuilt.bestBid().get()).isLessThan(rebuilt.bestAsk().get());
            }
        }
    }
}
