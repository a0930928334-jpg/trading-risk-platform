package com.shuhongyi.trading.trade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuhongyi.trading.account.CashBalance;
import com.shuhongyi.trading.account.CashBalanceRepository;
import com.shuhongyi.trading.account.CashLedgerEntry;
import com.shuhongyi.trading.account.CashLedgerEntryRepository;
import com.shuhongyi.trading.account.CashLedgerEntryType;
import com.shuhongyi.trading.instrument.Instrument;
import com.shuhongyi.trading.instrument.InstrumentRepository;
import com.shuhongyi.trading.matching.BookOrder;
import com.shuhongyi.trading.order.OrderBookRegistry;
import com.shuhongyi.trading.order.OrderCommandService;
import com.shuhongyi.trading.order.OrderRepository;
import com.shuhongyi.trading.position.Position;
import com.shuhongyi.trading.position.PositionRepository;
import com.shuhongyi.trading.support.IntegrationTest;
import com.shuhongyi.trading.support.TestFunding;
import com.shuhongyi.trading.support.TestInstruments;
import com.shuhongyi.trading.support.TestOrders;
import com.shuhongyi.trading.support.TestUser;
import com.shuhongyi.trading.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class MatchingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private CashBalanceRepository cashBalanceRepository;

    @Autowired
    private CashLedgerEntryRepository ledgerRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private OrderBookRegistry orderBooks;

    @Autowired
    private TestFunding testFunding;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Instrument instrument;
    private TestUser buyer;
    private TestUser seller;

    @BeforeEach
    void setUp() throws Exception {
        instrument = TestInstruments.create(instrumentRepository, "100.0000");
        buyer = TestUsers.create(mockMvc, objectMapper);
        seller = TestUsers.create(mockMvc, objectMapper);
        testFunding.depositCash(buyer.accountId(), "10000.0000");
        testFunding.grantShares(seller.accountId(), instrument.getId(), 100);
    }

    @Test
    void crossingOrdersTradeAtTheMakerPriceAndSettleBothSides() throws Exception {
        long sellId = place(seller, "SELL", "100.0000", 10).get("id").asLong();

        JsonNode buy = place(buyer, "BUY", "100.0000", 10);

        assertThat(buy.get("status").asText()).isEqualTo("FILLED");
        assertThat(buy.get("filledQuantity").asLong()).isEqualTo(10);
        assertThat(orderRepository.findById(sellId)).get()
                .extracting(order -> order.getStatus().name()).isEqualTo("FILLED");

        List<Trade> trades = tradeRepository.findByInstrumentIdOrderByIdAsc(instrument.getId());
        assertThat(trades).singleElement().satisfies(trade -> {
            assertThat(trade.getPrice()).isEqualByComparingTo("100.0000");
            assertThat(trade.getQuantity()).isEqualTo(10);
            assertThat(trade.getBuyOrderId()).isEqualTo(buy.get("id").asLong());
            assertThat(trade.getSellOrderId()).isEqualTo(sellId);
        });

        assertCash(buyer, "9000.0000", "0.0000");
        assertCash(seller, "1000.0000", "0.0000");
        assertPosition(buyer, 10, 0);
        assertPosition(seller, 90, 0);
        assertThat(instrumentRepository.findById(instrument.getId()).orElseThrow().getLastTradePrice())
                .isEqualByComparingTo("100.0000");

        assertThat(ledgerTypes(buyer)).containsExactly(
                CashLedgerEntryType.DEPOSIT, CashLedgerEntryType.RESERVE, CashLedgerEntryType.TRADE_DEBIT);
        assertThat(ledgerTypes(seller)).containsExactly(CashLedgerEntryType.TRADE_CREDIT);
        assertThat(lastLedgerEntry(seller).getTradeId()).isEqualTo(trades.get(0).getId());
    }

    @Test
    void buyerTakingABetterPricePaysTheMakerPriceAndGetsTheDifferenceReleased() throws Exception {
        place(seller, "SELL", "98.0000", 10);

        JsonNode buy = place(buyer, "BUY", "105.0000", 10);

        assertThat(buy.get("status").asText()).isEqualTo("FILLED");
        assertCash(buyer, "9020.0000", "0.0000");
        CashLedgerEntry release = lastLedgerEntry(buyer);
        assertThat(release.getEntryType()).isEqualTo(CashLedgerEntryType.RELEASE);
        assertThat(release.getAmount()).isEqualByComparingTo("70.0000");
    }

    @Test
    void sellerTakingABetterBidReceivesTheBidPrice() throws Exception {
        place(buyer, "BUY", "102.0000", 10);

        JsonNode sell = place(seller, "SELL", "99.0000", 10);

        assertThat(sell.get("status").asText()).isEqualTo("FILLED");
        assertCash(seller, "1020.0000", "0.0000");
        assertCash(buyer, "8980.0000", "0.0000");
    }

    @Test
    void partialFillLeavesBothSidesConsistentAndTheRemainderCanBeCancelled() throws Exception {
        long sellId = place(seller, "SELL", "100.0000", 4).get("id").asLong();

        JsonNode buy = place(buyer, "BUY", "101.0000", 10);

        assertThat(buy.get("status").asText()).isEqualTo("PARTIALLY_FILLED");
        assertThat(buy.get("remainingQuantity").asLong()).isEqualTo(6);
        // Paid 4 x 100 = 400; still reserving 6 x 101 = 606 for the resting remainder.
        assertCash(buyer, "9600.0000", "606.0000");
        assertThat(orderBooks.book(instrument.getId()).find(buy.get("id").asLong()))
                .get().extracting(BookOrder::remainingQuantity).isEqualTo(6L);
        assertThat(orderBooks.book(instrument.getId()).find(sellId)).isEmpty();

        mockMvc.perform(delete("/api/orders/{id}", buy.get("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, buyer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.filledQuantity").value(4));
        assertCash(buyer, "9600.0000", "0.0000");
        assertThat(orderBooks.book(instrument.getId()).size()).isZero();
    }

    @Test
    void restingMakerIsPartiallyFilledAndKeepsItsPlace() throws Exception {
        long sellId = place(seller, "SELL", "100.0000", 10).get("id").asLong();

        place(buyer, "BUY", "100.0000", 3);

        assertThat(orderRepository.findById(sellId)).get().satisfies(order -> {
            assertThat(order.getStatus().name()).isEqualTo("PARTIALLY_FILLED");
            assertThat(order.getFilledQuantity()).isEqualTo(3);
        });
        assertPosition(seller, 97, 7);
    }

    @Test
    void largeOrderSweepsSeveralPriceLevels() throws Exception {
        TestUser secondSeller = TestUsers.create(mockMvc, objectMapper);
        testFunding.grantShares(secondSeller.accountId(), instrument.getId(), 100);
        place(seller, "SELL", "101.0000", 5);
        place(secondSeller, "SELL", "100.0000", 5);
        place(seller, "SELL", "102.0000", 5);

        JsonNode buy = place(buyer, "BUY", "101.5000", 12);

        assertThat(buy.get("filledQuantity").asLong()).isEqualTo(10);
        assertThat(tradeRepository.findByInstrumentIdOrderByIdAsc(instrument.getId()))
                .extracting(trade -> trade.getPrice().stripTrailingZeros().toPlainString())
                .containsExactly("100", "101");
        // 5 x 100 + 5 x 101 = 1005 paid; 2 x 101.5 = 203 still reserved.
        assertCash(buyer, "8995.0000", "203.0000");
    }

    @Test
    void selfTradeIsPreventedByCancellingTheNewOrder() throws Exception {
        testFunding.depositCash(seller.accountId(), "10000.0000");
        long restingSellId = place(seller, "SELL", "100.0000", 5).get("id").asLong();

        JsonNode ownBuy = place(seller, "BUY", "100.0000", 5);

        assertThat(ownBuy.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(ownBuy.get("rejectCode").asText()).isEqualTo(OrderCommandService.SELF_TRADE_PREVENTED);
        assertThat(ownBuy.get("filledQuantity").asLong()).isZero();
        assertThat(tradeRepository.findByInstrumentIdOrderByIdAsc(instrument.getId())).isEmpty();
        assertCash(seller, "10000.0000", "0.0000");
        assertThat(orderRepository.findById(restingSellId)).get()
                .extracting(order -> order.getStatus().name()).isEqualTo("NEW");
        assertThat(orderBooks.book(instrument.getId()).size()).isEqualTo(1);
    }

    @Test
    void selfTradePreventionKeepsFillsAgainstOtherAccounts() throws Exception {
        testFunding.depositCash(seller.accountId(), "10000.0000");
        TestUser other = TestUsers.create(mockMvc, objectMapper);
        testFunding.grantShares(other.accountId(), instrument.getId(), 10);
        place(other, "SELL", "99.0000", 3);
        place(seller, "SELL", "100.0000", 5);

        JsonNode ownBuy = place(seller, "BUY", "100.0000", 8);

        assertThat(ownBuy.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(ownBuy.get("filledQuantity").asLong()).isEqualTo(3);
        // Paid 3 x 99 = 297 to the other seller, nothing still reserved.
        assertCash(seller, "9703.0000", "0.0000");
    }

    @Test
    void tradesEndpointShowsEachSideWithItsOwnOrderAndLiquidity() throws Exception {
        long sellId = place(seller, "SELL", "100.0000", 2).get("id").asLong();
        long buyId = place(buyer, "BUY", "100.0000", 2).get("id").asLong();

        mockMvc.perform(get("/api/trades").header(HttpHeaders.AUTHORIZATION, buyer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].symbol").value(instrument.getSymbol()))
                .andExpect(jsonPath("$.content[0].side").value("BUY"))
                .andExpect(jsonPath("$.content[0].orderId").value(buyId))
                .andExpect(jsonPath("$.content[0].liquidity").value("TAKER"))
                .andExpect(jsonPath("$.content[0].value").value(200.0));

        mockMvc.perform(get("/api/trades").header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(jsonPath("$.content[0].side").value("SELL"))
                .andExpect(jsonPath("$.content[0].orderId").value(sellId))
                .andExpect(jsonPath("$.content[0].liquidity").value("MAKER"));
    }

    @Test
    void bookEndpointShowsAggregatedDepthBestPriceFirst() throws Exception {
        TestUser secondBuyer = TestUsers.create(mockMvc, objectMapper);
        testFunding.depositCash(secondBuyer.accountId(), "10000.0000");
        place(buyer, "BUY", "99.0000", 5);
        place(secondBuyer, "BUY", "99.0000", 7);
        place(buyer, "BUY", "98.5000", 1);
        place(seller, "SELL", "101.0000", 4);

        mockMvc.perform(get("/api/instruments/{symbol}/book", instrument.getSymbol())
                        .param("depth", "5")
                        .header(HttpHeaders.AUTHORIZATION, buyer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bids.length()").value(2))
                .andExpect(jsonPath("$.bids[0].price").value(99.0))
                .andExpect(jsonPath("$.bids[0].quantity").value(12))
                .andExpect(jsonPath("$.bids[0].orderCount").value(2))
                .andExpect(jsonPath("$.bids[1].price").value(98.5))
                .andExpect(jsonPath("$.asks[0].price").value(101.0))
                .andExpect(jsonPath("$.asks[0].quantity").value(4));
    }

    @Test
    void lastTradePriceMovesThePriceBand() throws Exception {
        place(seller, "SELL", "109.0000", 1);
        place(buyer, "BUY", "109.0000", 1);

        // 119 is 19% above the reference price but within 10% of the last trade at 109.
        JsonNode next = place(buyer, "BUY", "119.0000", 1);

        assertThat(next.get("status").asText()).isEqualTo("NEW");
    }

    @Test
    void bookIsRebuiltFromTheDatabase() throws Exception {
        long sellId = place(seller, "SELL", "100.0000", 10).get("id").asLong();
        place(buyer, "BUY", "100.0000", 4);

        var rebuilt = orderBooks.rebuild(instrument.getId());

        assertThat(rebuilt.find(sellId)).get().extracting(BookOrder::remainingQuantity).isEqualTo(6L);
        assertThat(rebuilt.size()).isEqualTo(1);
    }

    @Test
    void failedTransactionRollsBackAndRebuildsTheBookFromTheDatabase() throws Exception {
        long sellId = place(seller, "SELL", "100.0000", 5).get("id").asLong();
        // Make the in-memory book stale: the database says the order is gone, the book still has it.
        jdbcTemplate.update("UPDATE orders SET status = 'CANCELLED' WHERE id = ?", sellId);
        jdbcTemplate.update("UPDATE positions SET reserved_quantity = 0 WHERE account_id = ? AND instrument_id = ?",
                seller.accountId(), instrument.getId());
        long ordersBefore = orderRepository.count();

        JsonNode failed = place(buyer, "BUY", "100.0000", 5);

        assertThat(failed.get("status").asInt()).isEqualTo(500);
        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
        assertCash(buyer, "10000.0000", "0.0000");
        assertThat(orderBooks.book(instrument.getId()).find(sellId)).isEmpty();

        JsonNode retry = place(buyer, "BUY", "100.0000", 5);
        assertThat(retry.get("status").asText()).isEqualTo("NEW");
    }

    @Test
    void openOrdersSurviveARestartOfTheBooks() throws Exception {
        long sellId = place(seller, "SELL", "100.0000", 5).get("id").asLong();

        // Simulates startup: every book with open orders is reloaded from the database.
        orderBooks.afterSingletonsInstantiated();
        JsonNode buy = place(buyer, "BUY", "100.0000", 5);

        assertThat(buy.get("status").asText()).isEqualTo("FILLED");
        assertThat(tradeRepository.findByInstrumentIdOrderByIdAsc(instrument.getId()))
                .singleElement().extracting(Trade::getSellOrderId).isEqualTo(sellId);
    }

    private JsonNode place(TestUser user, String side, String price, long quantity) throws Exception {
        return TestOrders.place(mockMvc, objectMapper, user, instrument.getSymbol(), side, price, quantity);
    }

    private void assertCash(TestUser user, String balance, String reserved) {
        CashBalance cash = cashBalanceRepository.findByAccount_Id(user.accountId()).orElseThrow();
        assertThat(cash.getBalance()).as("balance").isEqualByComparingTo(balance);
        assertThat(cash.getReserved()).as("reserved").isEqualByComparingTo(reserved);
    }

    private void assertPosition(TestUser user, long quantity, long reserved) {
        Position position = positionRepository.findByAccountIdAndInstrumentId(user.accountId(), instrument.getId())
                .orElseThrow();
        assertThat(position.getQuantity()).as("quantity").isEqualTo(quantity);
        assertThat(position.getReservedQuantity()).as("reserved quantity").isEqualTo(reserved);
    }

    private List<CashLedgerEntryType> ledgerTypes(TestUser user) {
        return ledgerRepository.findByAccountIdOrderByIdAsc(user.accountId()).stream()
                .map(CashLedgerEntry::getEntryType)
                .toList();
    }

    private CashLedgerEntry lastLedgerEntry(TestUser user) {
        List<CashLedgerEntry> entries = ledgerRepository.findByAccountIdOrderByIdAsc(user.accountId());
        return entries.get(entries.size() - 1);
    }
}
