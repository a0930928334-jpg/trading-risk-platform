package com.shuhongyi.trading.auth;

import com.shuhongyi.trading.account.Account;
import com.shuhongyi.trading.account.AccountRepository;
import com.shuhongyi.trading.account.AccountStatus;
import com.shuhongyi.trading.account.CashBalance;
import com.shuhongyi.trading.account.CashBalanceRepository;
import com.shuhongyi.trading.auth.dto.RegisterRequest;
import com.shuhongyi.trading.auth.dto.RegisterResponse;
import com.shuhongyi.trading.auth.exception.DuplicateEmailException;
import com.shuhongyi.trading.user.AppUser;
import com.shuhongyi.trading.user.AppUserRepository;
import com.shuhongyi.trading.user.UserStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

@Service
public class AuthService {

    private static final String DEFAULT_CURRENCY = "USD";
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("0.0000");

    private final AppUserRepository appUserRepository;
    private final AccountRepository accountRepository;
    private final CashBalanceRepository cashBalanceRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            AppUserRepository appUserRepository,
            AccountRepository accountRepository,
            CashBalanceRepository cashBalanceRepository,
            PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.accountRepository = accountRepository;
        this.cashBalanceRepository = cashBalanceRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        String normalizedFullName = request.fullName().trim();

        if (appUserRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException(normalizedEmail);
        }

        String passwordHash = passwordEncoder.encode(request.password());

        AppUser savedUser = appUserRepository.save(AppUser.builder()
                .email(normalizedEmail)
                .passwordHash(passwordHash)
                .fullName(normalizedFullName)
                .status(UserStatus.ACTIVE)
                .build());

        String accountNumber = "ACC-%06d".formatted(savedUser.getId());

        Account savedAccount = accountRepository.save(Account.builder()
                .user(savedUser)
                .accountNumber(accountNumber)
                .status(AccountStatus.ACTIVE)
                .currency(DEFAULT_CURRENCY)
                .build());

        cashBalanceRepository.save(CashBalance.builder()
                .account(savedAccount)
                .currency(DEFAULT_CURRENCY)
                .balance(INITIAL_BALANCE)
                .build());

        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getFullName(),
                savedAccount.getId(),
                savedAccount.getAccountNumber(),
                savedAccount.getCurrency()
        );
    }
}
