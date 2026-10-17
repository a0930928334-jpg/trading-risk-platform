package com.shuhongyi.trading.position;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The only component that changes positions. Requires an existing transaction; callers own the row locks.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class PositionService {

    private final PositionRepository positionRepository;

    public PositionService(PositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    /** Locks the position row, creating an empty one first if the account has never held the instrument. */
    public Position lock(Long accountId, Long instrumentId) {
        positionRepository.insertIfAbsent(accountId, instrumentId);
        return positionRepository.findForUpdate(accountId, instrumentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Position row missing after insert for account %d, instrument %d".formatted(accountId, instrumentId)));
    }

    /** Locks the positions of several accounts in one instrument, in ascending account ID order. */
    public Map<Long, Position> lockInOrder(Collection<Long> accountIds, Long instrumentId) {
        Map<Long, Position> locked = new LinkedHashMap<>();
        accountIds.stream().distinct().sorted().forEach(id -> locked.put(id, lock(id, instrumentId)));
        return locked;
    }

    public void reserve(Position position, long shares) {
        position.reserve(shares);
    }

    public void release(Position position, long shares) {
        position.release(shares);
    }

    public void debitReserved(Position position, long shares) {
        position.debitReserved(shares);
    }

    public void credit(Position position, long shares) {
        position.credit(shares);
    }
}
