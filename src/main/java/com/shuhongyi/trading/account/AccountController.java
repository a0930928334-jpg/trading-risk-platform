package com.shuhongyi.trading.account;

import com.shuhongyi.trading.account.dto.AccountResponse;
import com.shuhongyi.trading.account.dto.DepositRequest;
import com.shuhongyi.trading.account.dto.DepositResponse;
import com.shuhongyi.trading.account.dto.LedgerEntryResponse;
import com.shuhongyi.trading.auth.CurrentUserId;
import com.shuhongyi.trading.common.web.PageResponse;
import com.shuhongyi.trading.common.web.Pagination;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Account", description = "Cash balance, deposits and the cash ledger. USD only; amounts have 4 decimal places.")
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "Get account balance", description = "balance, reserved (held by open buy orders) and available = balance - reserved.")
    @ApiResponse(responseCode = "200", description = "Account summary")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @GetMapping
    public AccountResponse getAccount(@CurrentUserId Long userId) {
        return accountService.getAccountSummary(userId);
    }

    @Operation(summary = "Deposit cash", description = "Adds simulated cash (0.0001 to 1,000,000 per deposit) and records a DEPOSIT ledger entry.")
    @ApiResponse(responseCode = "201", description = "Deposited")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "409", description = "Account not active")
    @PostMapping("/deposits")
    public ResponseEntity<DepositResponse> deposit(
            @CurrentUserId Long userId,
            @Valid @RequestBody DepositRequest request) {
        DepositResponse response = accountService.deposit(userId, request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List cash ledger entries", description = "Every cash movement, newest first. Page size is capped at 100.")
    @ApiResponse(responseCode = "200", description = "Page of ledger entries")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @GetMapping("/ledger")
    public PageResponse<LedgerEntryResponse> getLedger(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + Pagination.DEFAULT_SIZE) int size) {
        return accountService.getLedger(userId, Pagination.newestFirst(page, size));
    }
}
