package com.shuhongyi.trading.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByAccountId(Long accountId, Pageable pageable);

    Page<Order> findByAccountIdAndStatus(Long accountId, OrderStatus status, Pageable pageable);

    Optional<Order> findByIdAndAccountId(Long id, Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    /** Open orders of one instrument in time priority, used to rebuild its order book. */
    List<Order> findByInstrumentIdAndStatusInOrderByIdAsc(Long instrumentId, Collection<OrderStatus> statuses);

    @Query("select distinct o.instrumentId from Order o where o.status in :statuses")
    List<Long> findInstrumentIdsWithStatusIn(@Param("statuses") Collection<OrderStatus> statuses);
}
