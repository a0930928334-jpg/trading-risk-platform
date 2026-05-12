package com.shuhongyi.trading.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemStatusController {

    @GetMapping("/api/system/status")
    public SystemStatusResponse getSystemStatus() {
        return new SystemStatusResponse(
                "trading-risk-platform",
                "UP",
                "INFRASTRUCTURE_SETUP"
        );
    }
}