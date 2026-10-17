package com.shuhongyi.trading.user;

import com.shuhongyi.trading.auth.CurrentUserId;
import com.shuhongyi.trading.user.dto.MeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public MeResponse me(@CurrentUserId Long userId) {
        return userService.getMe(userId);
    }
}
