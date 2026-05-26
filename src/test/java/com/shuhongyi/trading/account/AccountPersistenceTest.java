package com.shuhongyi.trading.account;

import com.shuhongyi.trading.user.AppUser;
import com.shuhongyi.trading.user.AppUserRepository;
import com.shuhongyi.trading.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AccountPersistenceTest {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CashBalanceRepository cashBalanceRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldPersistAppUserAccountAndCashBalance() {
        AppUser savedUser = appUserRepository.save(AppUser.builder()
                .email("alice.persistence@example.com")
                .passwordHash("hashed-password")
                .fullName("Alice Persistence")
                .status(UserStatus.ACTIVE)
                .build());

        Account savedAccount = accountRepository.save(Account.builder()
                .user(savedUser)
                .accountNumber("ACC-PERSIST-001")
                .status(AccountStatus.ACTIVE)
                .currency("USD")
                .build());

        CashBalance savedCashBalance = cashBalanceRepository.save(CashBalance.builder()
                .account(savedAccount)
                .currency("USD")
                .balance(new BigDecimal("10000.0000"))
                .build());

        Long savedUserId = savedUser.getId();
        Long savedAccountId = savedAccount.getId();

        entityManager.flush();
        entityManager.clear();

        AppUser reloadedUser = appUserRepository.findByEmail("alice.persistence@example.com").orElseThrow();
        Account reloadedAccount = accountRepository.findByAccountNumber("ACC-PERSIST-001").orElseThrow();
        CashBalance reloadedCashBalance = cashBalanceRepository.findByAccount_Id(savedAccountId).orElseThrow();

        assertThat(reloadedUser).isNotNull();
        assertThat(reloadedAccount).isNotNull();
        assertThat(reloadedCashBalance).isNotNull();
        assertThat(reloadedAccount.getUser().getId()).isEqualTo(savedUserId);
        assertThat(reloadedCashBalance.getAccount().getId()).isEqualTo(savedAccountId);
        assertThat(reloadedCashBalance.getCurrency()).isEqualTo("USD");
        assertThat(reloadedCashBalance.getBalance()).isEqualByComparingTo(new BigDecimal("10000.0000"));
    }
}
