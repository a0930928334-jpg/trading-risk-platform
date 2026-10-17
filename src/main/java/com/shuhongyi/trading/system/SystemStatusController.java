package com.shuhongyi.trading.system;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "System")
@SecurityRequirements
public class SystemStatusController {

    @Operation(summary = "Get application status")
    @ApiResponse(responseCode = "200", description = "Status")
    @GetMapping("/api/system/status")
    public SystemStatusResponse getSystemStatus() {
        return new SystemStatusResponse(
                "trading-risk-platform",
                "UP",
                "INFRASTRUCTURE_SETUP"
        );
    }
}