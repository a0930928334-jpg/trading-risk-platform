package com.shuhongyi.trading.user;

import com.shuhongyi.trading.account.Account;
import com.shuhongyi.trading.account.AccountRepository;
import com.shuhongyi.trading.user.dto.MeResponse;
import com.shuhongyi.trading.user.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final AppUserRepository appUserRepository;
    private final AccountRepository accountRepository;

    public UserService(AppUserRepository appUserRepository, AccountRepository accountRepository) {
        this.appUserRepository = appUserRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public MeResponse getMe(Long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Account account = accountRepository.findFirstByUser_IdOrderByIdAsc(userId)
                .orElse(null);

        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                account == null ? null : account.getId(),
                account == null ? null : account.getAccountNumber(),
                account == null ? null : account.getCurrency()
        );
    }
}
