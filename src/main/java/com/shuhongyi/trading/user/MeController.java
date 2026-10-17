package com.shuhongyi.trading.user;

import com.shuhongyi.trading.auth.CurrentUserId;
import com.shuhongyi.trading.user.dto.MeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Current user")
@RequestMapping("/api/me")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Get the authenticated user", description = "User details and account identifiers for the subject of the bearer token.")
    @ApiResponse(responseCode = "200", description = "Current user")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @GetMapping
    public MeResponse me(@CurrentUserId Long userId) {
        return userService.getMe(userId);
    }
}
