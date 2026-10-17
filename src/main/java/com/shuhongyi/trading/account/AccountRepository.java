package com.shuhongyi.trading.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByUser_Id(Long userId);

    /** Each user has exactly one account in v0.1; the lowest ID is the primary one. */
    Optional<Account> findFirstByUser_IdOrderByIdAsc(Long userId);
}
