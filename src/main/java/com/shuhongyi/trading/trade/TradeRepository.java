package com.shuhongyi.trading.trade;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    @Query("select t from Trade t where t.buyerAccountId = :accountId or t.sellerAccountId = :accountId")
    Page<Trade> findByAccountId(@Param("accountId") Long accountId, Pageable pageable);

    List<Trade> findByInstrumentIdOrderByIdAsc(Long instrumentId);
}
