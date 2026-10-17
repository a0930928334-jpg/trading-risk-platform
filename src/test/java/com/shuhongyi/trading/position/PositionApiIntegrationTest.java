package com.shuhongyi.trading.position;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuhongyi.trading.instrument.Instrument;
import com.shuhongyi.trading.instrument.InstrumentRepository;
import com.shuhongyi.trading.support.IntegrationTest;
import com.shuhongyi.trading.support.TestFunding;
import com.shuhongyi.trading.support.TestInstruments;
import com.shuhongyi.trading.support.TestUser;
import com.shuhongyi.trading.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class PositionApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private PositionService positionService;

    @Autowired
    private TestFunding testFunding;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void newUserHasNoPositions() throws Exception {
        TestUser user = TestUsers.create(mockMvc, objectMapper);

        mockMvc.perform(get("/api/positions").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void heldSharesAreListedWithAvailableQuantity() throws Exception {
        TestUser user = TestUsers.create(mockMvc, objectMapper);
        Instrument instrument = TestInstruments.create(instrumentRepository, "50.0000");
        testFunding.grantShares(user.accountId(), instrument.getId(), 40);
        transactionTemplate.executeWithoutResult(status ->
                positionService.reserve(positionService.lock(user.accountId(), instrument.getId()), 15));

        mockMvc.perform(get("/api/positions").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].symbol").value(instrument.getSymbol()))
                .andExpect(jsonPath("$.content[0].quantity").value(40))
                .andExpect(jsonPath("$.content[0].reservedQuantity").value(15))
                .andExpect(jsonPath("$.content[0].availableQuantity").value(25));
    }

    @Test
    void emptyPositionRowsAreHiddenAndOtherUsersPositionsAreInvisible() throws Exception {
        TestUser alice = TestUsers.create(mockMvc, objectMapper);
        TestUser bob = TestUsers.create(mockMvc, objectMapper);
        Instrument instrument = TestInstruments.create(instrumentRepository, "10.0000");
        testFunding.grantShares(alice.accountId(), instrument.getId(), 5);
        // Locking creates an empty row for Bob; it must not show up as a holding.
        transactionTemplate.executeWithoutResult(status -> positionService.lock(bob.accountId(), instrument.getId()));

        mockMvc.perform(get("/api/positions").header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void lockCreatesMissingRowOnceAndRequiresATransaction() throws Exception {
        TestUser user = TestUsers.create(mockMvc, objectMapper);
        Instrument instrument = TestInstruments.create(instrumentRepository, "10.0000");

        transactionTemplate.executeWithoutResult(status -> {
            positionService.lock(user.accountId(), instrument.getId());
            positionService.lock(user.accountId(), instrument.getId());
        });

        assertThat(positionRepository.findByAccountIdAndInstrumentId(user.accountId(), instrument.getId()))
                .get().extracting(Position::getQuantity).isEqualTo(0L);
        assertThatThrownBy(() -> positionService.lock(user.accountId(), instrument.getId()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void databaseRejectsNegativeOrOverReservedPositions() throws Exception {
        TestUser user = TestUsers.create(mockMvc, objectMapper);
        Instrument instrument = TestInstruments.create(instrumentRepository, "10.0000");
        testFunding.grantShares(user.accountId(), instrument.getId(), 5);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE positions SET quantity = -1 WHERE account_id = ? AND instrument_id = ?",
                user.accountId(), instrument.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE positions SET reserved_quantity = 6 WHERE account_id = ? AND instrument_id = ?",
                user.accountId(), instrument.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void positionsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/positions")).andExpect(status().isUnauthorized());
    }
}
