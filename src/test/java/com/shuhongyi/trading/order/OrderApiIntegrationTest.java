package com.shuhongyi.trading.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuhongyi.trading.account.CashBalance;
import com.shuhongyi.trading.account.CashBalanceRepository;
import com.shuhongyi.trading.account.CashLedgerEntry;
import com.shuhongyi.trading.account.CashLedgerEntryRepository;
import com.shuhongyi.trading.account.CashLedgerEntryType;
import com.shuhongyi.trading.instrument.Instrument;
import com.shuhongyi.trading.instrument.InstrumentRepository;
import com.shuhongyi.trading.instrument.InstrumentStatus;
import com.shuhongyi.trading.position.Position;
import com.shuhongyi.trading.position.PositionRepository;
import com.shuhongyi.trading.risk.RiskEngine;
import com.shuhongyi.trading.risk.rules.AccountStatusRule;
import com.shuhongyi.trading.risk.rules.AvailableCashRule;
import com.shuhongyi.trading.risk.rules.AvailablePositionRule;
import com.shuhongyi.trading.risk.rules.InstrumentStatusRule;
import com.shuhongyi.trading.risk.rules.MaxOrderNotionalRule;
import com.shuhongyi.trading.risk.rules.PriceBandRule;
import com.shuhongyi.trading.support.IntegrationTest;
import com.shuhongyi.trading.support.TestFunding;
import com.shuhongyi.trading.support.TestInstruments;
import com.shuhongyi.trading.support.TestUser;
import com.shuhongyi.trading.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class OrderApiIntegrationTest {

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
    private RiskEngine riskEngine;

    @Autowired
    private TestFunding testFunding;

    private TestUser user;
    private Instrument instrument;

    @BeforeEach
    void setUp() throws Exception {
        user = TestUsers.create(mockMvc, objectMapper);
        instrument = TestInstruments.create(instrumentRepository, "100.0000");
    }

    @Test
    void acceptedBuyOrderReservesCashAndIsLedgered() throws Exception {
        testFunding.depositCash(user.accountId(), "5000.0000");

        JsonNode order = read(place(user, instrument.getSymbol(), "BUY", "101.5000", 20)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith("/api/orders/")))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.symbol").value(instrument.getSymbol()))
                .andExpect(jsonPath("$.type").value("LIMIT"))
                .andExpect(jsonPath("$.timeInForce").value("GTC"))
                .andExpect(jsonPath("$.filledQuantity").value(0))
                .andExpect(jsonPath("$.remainingQuantity").value(20)));

        CashBalance cash = cash(user);
        assertThat(cash.getReserved()).isEqualByComparingTo("2030.0000");
        assertThat(cash.getAvailable()).isEqualByComparingTo("2970.0000");

        CashLedgerEntry reserve = lastLedgerEntry(user);
        assertThat(reserve.getEntryType()).isEqualTo(CashLedgerEntryType.RESERVE);
        assertThat(reserve.getAmount()).isEqualByComparingTo("2030.0000");
        assertThat(reserve.getOrderId()).isEqualTo(order.get("id").asLong());
    }

    @Test
    void acceptedSellOrderReservesShares() throws Exception {
        testFunding.grantShares(user.accountId(), instrument.getId(), 50);

        place(user, instrument.getSymbol(), "SELL", "99.0000", 30)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"));

        Position position = position(user);
        assertThat(position.getQuantity()).isEqualTo(50);
        assertThat(position.getReservedQuantity()).isEqualTo(30);
    }

    @Test
    void insufficientFundsIsRejectedStoredAndReservesNothing() throws Exception {
        testFunding.depositCash(user.accountId(), "100.0000");

        JsonNode order = read(place(user, instrument.getSymbol(), "BUY", "100.0000", 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectCode").value(AvailableCashRule.CODE))
                .andExpect(jsonPath("$.rejectReason").value("Required 200.0000, available 100.0000"))
                .andExpect(jsonPath("$.remainingQuantity").value(0)));

        assertThat(orderRepository.findById(order.get("id").asLong())).get()
                .extracting(Order::getStatus).isEqualTo(OrderStatus.REJECTED);
        assertThat(cash(user).getReserved()).isEqualByComparingTo("0");
        assertThat(lastLedgerEntry(user).getEntryType()).isEqualTo(CashLedgerEntryType.DEPOSIT);
    }

    @Test
    void sellWithoutSharesIsRejectedBecauseShortSellingIsNotAllowed() throws Exception {
        place(user, instrument.getSymbol(), "SELL", "100.0000", 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectCode").value(AvailablePositionRule.CODE));
    }

    @Test
    void orderAboveMaxNotionalIsRejected() throws Exception {
        testFunding.depositCash(user.accountId(), "1000000.0000");

        place(user, instrument.getSymbol(), "BUY", "100.0000", 1001)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectCode").value(MaxOrderNotionalRule.CODE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"89.9900", "110.0100"})
    void priceOutsideTheBandIsRejected(String price) throws Exception {
        testFunding.depositCash(user.accountId(), "10000.0000");

        place(user, instrument.getSymbol(), "BUY", price, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectCode").value(PriceBandRule.CODE));
    }

    @Test
    void haltedInstrumentRejectsOrders() throws Exception {
        testFunding.depositCash(user.accountId(), "10000.0000");
        instrument.setStatus(InstrumentStatus.HALTED);
        instrumentRepository.saveAndFlush(instrument);

        place(user, instrument.getSymbol(), "BUY", "100.0000", 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectCode").value(InstrumentStatusRule.CODE));
    }

    @Test
    void riskRulesRunInDeclaredOrder() {
        assertThat(riskEngine.rules()).extracting(rule -> (Class) rule.getClass()).containsExactly(
                AccountStatusRule.class,
                InstrumentStatusRule.class,
                MaxOrderNotionalRule.class,
                PriceBandRule.class,
                AvailableCashRule.class,
                AvailablePositionRule.class);
    }

    @Test
    void unknownSymbolIsNotFoundAndNotStored() throws Exception {
        long before = orderRepository.count();

        place(user, "NOPE", "BUY", "1.0000", 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("instrument-not-found"));

        assertThat(orderRepository.count()).isEqualTo(before);
    }

    @Test
    void symbolIsCaseInsensitive() throws Exception {
        testFunding.depositCash(user.accountId(), "1000.0000");

        place(user, instrument.getSymbol().toLowerCase(), "BUY", "100.0000", 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value(instrument.getSymbol()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"price\":10,\"quantity\":0}",
            "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"price\":-1,\"quantity\":1}",
            "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"price\":1.00001,\"quantity\":1}",
            "{\"symbol\":\"AAPL\",\"price\":10,\"quantity\":1}",
            "{\"side\":\"BUY\",\"price\":10,\"quantity\":1}",
            "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}"
    })
    void invalidRequestsAreRejectedWithoutStoringAnOrder(String body) throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation-failed"));
    }

    @Test
    void marketOrdersAreNotSupported() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"type\":\"MARKET\",\"price\":10,\"quantity\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancellingABuyOrderReleasesTheReservation() throws Exception {
        testFunding.depositCash(user.accountId(), "1000.0000");
        long orderId = read(place(user, instrument.getSymbol(), "BUY", "100.0000", 5)).get("id").asLong();

        mockMvc.perform(delete("/api/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.remainingQuantity").value(0));

        assertThat(cash(user).getReserved()).isEqualByComparingTo("0");
        assertThat(cash(user).getAvailable()).isEqualByComparingTo("1000.0000");
        CashLedgerEntry release = lastLedgerEntry(user);
        assertThat(release.getEntryType()).isEqualTo(CashLedgerEntryType.RELEASE);
        assertThat(release.getAmount()).isEqualByComparingTo("500.0000");
        assertThat(release.getOrderId()).isEqualTo(orderId);
    }

    @Test
    void cancellingASellOrderReleasesShares() throws Exception {
        testFunding.grantShares(user.accountId(), instrument.getId(), 10);
        long orderId = read(place(user, instrument.getSymbol(), "SELL", "100.0000", 10)).get("id").asLong();

        mockMvc.perform(delete("/api/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk());

        assertThat(position(user).getReservedQuantity()).isZero();
        assertThat(position(user).getAvailableQuantity()).isEqualTo(10);
    }

    @Test
    void closedOrdersCannotBeCancelled() throws Exception {
        testFunding.depositCash(user.accountId(), "1000.0000");
        long orderId = read(place(user, instrument.getSymbol(), "BUY", "100.0000", 1)).get("id").asLong();
        mockMvc.perform(delete("/api/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, user.bearer()));

        mockMvc.perform(delete("/api/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("order-not-cancellable"));

        long rejectedId = read(place(user, instrument.getSymbol(), "SELL", "100.0000", 1)).get("id").asLong();
        mockMvc.perform(delete("/api/orders/{id}", rejectedId).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isConflict());
    }

    @Test
    void otherUsersOrdersAreInvisible() throws Exception {
        testFunding.depositCash(user.accountId(), "1000.0000");
        long orderId = read(place(user, instrument.getSymbol(), "BUY", "100.0000", 1)).get("id").asLong();
        TestUser intruder = TestUsers.create(mockMvc, objectMapper);

        mockMvc.perform(get("/api/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, intruder.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, intruder.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, intruder.bearer()))
                .andExpect(jsonPath("$.totalElements").value(0));
        assertThat(orderRepository.findById(orderId)).get().extracting(Order::getStatus).isEqualTo(OrderStatus.NEW);
    }

    @Test
    void ordersAreListedNewestFirstAndCanBeFilteredByStatus() throws Exception {
        testFunding.depositCash(user.accountId(), "1000.0000");
        long first = read(place(user, instrument.getSymbol(), "BUY", "100.0000", 1)).get("id").asLong();
        long rejected = read(place(user, instrument.getSymbol(), "SELL", "100.0000", 1)).get("id").asLong();
        long third = read(place(user, instrument.getSymbol(), "BUY", "100.0000", 2)).get("id").asLong();

        mockMvc.perform(get("/api/orders").param("size", "2").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(third))
                .andExpect(jsonPath("$.content[1].id").value(rejected))
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get("/api/orders").param("status", "REJECTED").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(rejected));

        mockMvc.perform(get("/api/orders/{id}", first).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(100.0));
    }

    @Test
    void ordersRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions place(TestUser owner, String symbol, String side, String price, long quantity)
            throws Exception {
        String body = """
                {"symbol": "%s", "side": "%s", "price": %s, "quantity": %d}
                """.formatted(symbol, side, price, quantity);
        return mockMvc.perform(post("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private CashBalance cash(TestUser owner) {
        return cashBalanceRepository.findByAccount_Id(owner.accountId()).orElseThrow();
    }

    private Position position(TestUser owner) {
        return positionRepository.findByAccountIdAndInstrumentId(owner.accountId(), instrument.getId()).orElseThrow();
    }

    private CashLedgerEntry lastLedgerEntry(TestUser owner) {
        List<CashLedgerEntry> entries = ledgerRepository.findByAccountIdOrderByIdAsc(owner.accountId());
        return entries.get(entries.size() - 1);
    }
}
