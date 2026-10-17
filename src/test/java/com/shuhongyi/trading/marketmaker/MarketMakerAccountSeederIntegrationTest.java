package com.shuhongyi.trading.marketmaker;

import com.shuhongyi.trading.account.Account;
import com.shuhongyi.trading.account.CashBalance;
import com.shuhongyi.trading.account.CashBalanceRepository;
import com.shuhongyi.trading.account.CashLedgerEntry;
import com.shuhongyi.trading.account.CashLedgerEntryRepository;
import com.shuhongyi.trading.account.CashLedgerEntryType;
import com.shuhongyi.trading.instrument.InstrumentRepository;
import com.shuhongyi.trading.position.PositionRepository;
import com.shuhongyi.trading.support.IntegrationTest;
import com.shuhongyi.trading.user.AppUserRepository;
import com.shuhongyi.trading.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Calls the seeder directly. The startup runner is dev-only, so it never runs in tests.
 */
@IntegrationTest
class MarketMakerAccountSeederIntegrationTest {

    @Autowired
    private MarketMakerAccountSeeder seeder;

    @Autowired
    private MarketMakerProperties properties;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private CashBalanceRepository cashBalanceRepository;

    @Autowired
    private CashLedgerEntryRepository ledgerRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void seedCreatesFundedSystemAccountOnceAndIsIdempotent() {
        Account first = seeder.seed();
        Account second = seeder.seed();

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(appUserRepository.findByEmail(properties.email()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.SYSTEM);

        CashBalance cash = cashBalanceRepository.findByAccount_Id(first.getId()).orElseThrow();
        assertThat(cash.getBalance()).isEqualByComparingTo(properties.initialCash());

        List<CashLedgerEntry> ledger = ledgerRepository.findByAccountIdOrderByIdAsc(first.getId());
        assertThat(ledger).singleElement()
                .extracting(CashLedgerEntry::getEntryType).isEqualTo(CashLedgerEntryType.DEPOSIT);

        instrumentRepository.findAll().stream()
                .filter(instrument -> List.of("AAPL", "MSFT", "NVDA", "TSLA", "AMZN").contains(instrument.getSymbol()))
                .forEach(instrument -> assertThat(
                        positionRepository.findByAccountIdAndInstrumentId(first.getId(), instrument.getId()))
                        .get()
                        .extracting(p -> p.getQuantity())
                        .isEqualTo(properties.sharesPerInstrument()));
    }

    @Test
    void systemUserCannotLogIn() throws Exception {
        seeder.seed();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"anything\"}".formatted(properties.email())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void startupRunnerIsNotRegisteredOutsideDevProfile() {
        assertThat(applicationContext.containsBean("marketMakerRunner")).isFalse();
    }
}
