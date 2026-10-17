package com.shuhongyi.trading.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CashBalanceRepository extends JpaRepository<CashBalance, Long> {

    Optional<CashBalance> findByAccount_Id(Long accountId);

    /** {@code SELECT ... FOR UPDATE}: blocks other writers of this row until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CashBalance c where c.account.id = :accountId")
    Optional<CashBalance> findByAccountIdForUpdate(@Param("accountId") Long accountId);
}
