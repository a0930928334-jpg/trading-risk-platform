package com.shuhongyi.trading.position;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PositionRepository extends JpaRepository<Position, Long> {

    Optional<Position> findByAccountIdAndInstrumentId(Long accountId, Long instrumentId);

    @Query("select p from Position p where p.accountId = :accountId and (p.quantity > 0 or p.reservedQuantity > 0)")
    Page<Position> findHeldByAccountId(@Param("accountId") Long accountId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Position p where p.accountId = :accountId and p.instrumentId = :instrumentId")
    Optional<Position> findForUpdate(@Param("accountId") Long accountId, @Param("instrumentId") Long instrumentId);

    /** Creates an empty position row if none exists, so it can then be locked with {@link #findForUpdate}. */
    @Modifying
    @Query(value = """
            INSERT INTO positions (account_id, instrument_id, quantity, reserved_quantity)
            VALUES (:accountId, :instrumentId, 0, 0)
            ON CONFLICT (account_id, instrument_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("accountId") Long accountId, @Param("instrumentId") Long instrumentId);

    @Query("select coalesce(sum(p.quantity), 0) from Position p where p.instrumentId = :instrumentId")
    long sumQuantityByInstrumentId(@Param("instrumentId") Long instrumentId);
}
