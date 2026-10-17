package com.shuhongyi.trading.position;

import com.shuhongyi.trading.account.Account;
import com.shuhongyi.trading.account.AccountService;
import com.shuhongyi.trading.common.web.PageResponse;
import com.shuhongyi.trading.instrument.Instrument;
import com.shuhongyi.trading.instrument.InstrumentRepository;
import com.shuhongyi.trading.position.dto.PositionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PositionQueryService {

    private final AccountService accountService;
    private final PositionRepository positionRepository;
    private final InstrumentRepository instrumentRepository;

    public PositionQueryService(
            AccountService accountService,
            PositionRepository positionRepository,
            InstrumentRepository instrumentRepository) {
        this.accountService = accountService;
        this.positionRepository = positionRepository;
        this.instrumentRepository = instrumentRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<PositionResponse> getPositions(Long userId, Pageable pageable) {
        Account account = accountService.getAccountForUser(userId);
        Page<Position> positions = positionRepository.findHeldByAccountId(account.getId(), pageable);

        Map<Long, String> symbols = instrumentRepository
                .findAllById(positions.map(Position::getInstrumentId).toSet()).stream()
                .collect(Collectors.toMap(Instrument::getId, Instrument::getSymbol));

        Function<Position, PositionResponse> mapper = position -> new PositionResponse(
                symbols.get(position.getInstrumentId()),
                position.getQuantity(),
                position.getReservedQuantity(),
                position.getAvailableQuantity());
        return PageResponse.from(positions, mapper);
    }
}
