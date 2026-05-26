package com.shuhongyi.trading.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CashBalanceRepository extends JpaRepository<CashBalance, Long> {

    Optional<CashBalance> findByAccount_Id(Long accountId);
}
