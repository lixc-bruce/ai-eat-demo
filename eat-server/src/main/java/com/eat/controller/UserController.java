package com.eat.controller;

import com.eat.common.R;
import com.eat.dto.request.LoginRequest;
import com.eat.dto.request.PreferenceUpdateRequest;
import com.eat.dto.request.SendCodeRequest;
import com.eat.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/send-code")
    public R<Void> sendCode(@RequestBody @Valid SendCodeRequest request) {
        userService.sendCode(request.getPhone());
        return R.ok();
    }

    @PostMapping("/login")
    public R<?> login(@RequestBody @Valid LoginRequest request) {
        return R.ok(userService.login(request.getPhone(), request.getCode()));
    }

    @GetMapping("/profile")
    public R<?> profile(@RequestAttribute("userId") Long userId) {
        return R.ok(userService.getProfile(userId));
    }

    @GetMapping("/preference")
    public R<?> getPreference(@RequestAttribute("userId") Long userId) {
        return R.ok(userService.getPreference(userId));
    }

    @PutMapping("/preference")
    public R<Void> updatePreference(@RequestAttribute("userId") Long userId,
                                     @RequestBody @Valid PreferenceUpdateRequest request) {
        userService.updatePreference(userId, request);
        return R.ok();
    }
}
