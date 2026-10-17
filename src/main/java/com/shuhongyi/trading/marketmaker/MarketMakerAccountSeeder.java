package com.shuhongyi.trading.marketmaker;

import com.shuhongyi.trading.account.Account;
import com.shuhongyi.trading.account.AccountRepository;
import com.shuhongyi.trading.account.AccountStatus;
import com.shuhongyi.trading.account.CashBalance;
import com.shuhongyi.trading.account.CashBalanceRepository;
import com.shuhongyi.trading.account.CashService;
import com.shuhongyi.trading.common.money.Money;
import com.shuhongyi.trading.instrument.Instrument;
import com.shuhongyi.trading.instrument.InstrumentRepository;
import com.shuhongyi.trading.position.Position;
import com.shuhongyi.trading.position.PositionService;
import com.shuhongyi.trading.user.AppUser;
import com.shuhongyi.trading.user.AppUserRepository;
import com.shuhongyi.trading.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Creates the market maker's system user, account, cash and share inventory once.
 * Idempotent: if the system user already exists, its account is returned unchanged.
 */
@Component
public class MarketMakerAccountSeeder {

    private static final Logger log = LoggerFactory.getLogger(MarketMakerAccountSeeder.class);
    private static final String ACCOUNT_NUMBER = "SYS-MM-000001";

    private final MarketMakerProperties properties;
    private final AppUserRepository appUserRepository;
    private final AccountRepository accountRepository;
    private final CashBalanceRepository cashBalanceRepository;
    private final InstrumentRepository instrumentRepository;
    private final CashService cashService;
    private final PositionService positionService;
    private final PasswordEncoder passwordEncoder;

    public MarketMakerAccountSeeder(
            MarketMakerProperties properties,
            AppUserRepository appUserRepository,
            AccountRepository accountRepository,
            CashBalanceRepository cashBalanceRepository,
            InstrumentRepository instrumentRepository,
            CashService cashService,
            PositionService positionService,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.appUserRepository = appUserRepository;
        this.accountRepository = accountRepository;
        this.cashBalanceRepository = cashBalanceRepository;
        this.instrumentRepository = instrumentRepository;
        this.cashService = cashService;
        this.positionService = positionService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Account seed() {
        return appUserRepository.findByEmail(properties.email())
                .flatMap(user -> accountRepository.findFirstByUser_IdOrderByIdAsc(user.getId()))
                .orElseGet(this::create);
    }

    private Account create() {
        AppUser user = appUserRepository.save(AppUser.builder()
                .email(properties.email())
                // Random secret that nobody knows; SYSTEM users are rejected at login anyway.
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .fullName("Simulated Market Maker")
                .status(UserStatus.SYSTEM)
                .build());

        Account account = accountRepository.save(Account.builder()
                .user(user)
                .accountNumber(ACCOUNT_NUMBER)
                .status(AccountStatus.ACTIVE)
                .currency(Money.CURRENCY)
                .build());

        cashBalanceRepository.save(CashBalance.builder()
                .account(account)
                .currency(Money.CURRENCY)
                .balance(Money.ZERO)
                .build());
        cashBalanceRepository.flush();

        CashBalance cash = cashService.lock(account.getId());
        cashService.deposit(cash, Money.of(properties.initialCash()));

        for (Instrument instrument : instrumentRepository.findAll()) {
            Position position = positionService.lock(account.getId(), instrument.getId());
            positionService.credit(position, properties.sharesPerInstrument());
        }

        log.info("Created dev market maker account {} with {} cash and {} shares per instrument",
                ACCOUNT_NUMBER, properties.initialCash(), properties.sharesPerInstrument());
        return account;
    }
}
