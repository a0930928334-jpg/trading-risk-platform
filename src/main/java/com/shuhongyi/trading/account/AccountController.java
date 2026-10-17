package com.shuhongyi.trading.account;

import com.shuhongyi.trading.account.dto.AccountResponse;
import com.shuhongyi.trading.account.dto.DepositRequest;
import com.shuhongyi.trading.account.dto.DepositResponse;
import com.shuhongyi.trading.account.dto.LedgerEntryResponse;
import com.shuhongyi.trading.auth.CurrentUserId;
import com.shuhongyi.trading.common.web.PageResponse;
import com.shuhongyi.trading.common.web.Pagination;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public AccountResponse getAccount(@CurrentUserId Long userId) {
        return accountService.getAccountSummary(userId);
    }

    @PostMapping("/deposits")
    public ResponseEntity<DepositResponse> deposit(
            @CurrentUserId Long userId,
            @Valid @RequestBody DepositRequest request) {
        DepositResponse response = accountService.deposit(userId, request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/ledger")
    public PageResponse<LedgerEntryResponse> getLedger(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + Pagination.DEFAULT_SIZE) int size) {
        return accountService.getLedger(userId, Pagination.newestFirst(page, size));
    }
}
