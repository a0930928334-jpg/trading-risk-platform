package com.shuhongyi.trading.account;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashLedgerEntryRepository extends JpaRepository<CashLedgerEntry, Long> {

    Page<CashLedgerEntry> findByAccountId(Long accountId, Pageable pageable);

    List<CashLedgerEntry> findByAccountIdOrderByIdAsc(Long accountId);
}
