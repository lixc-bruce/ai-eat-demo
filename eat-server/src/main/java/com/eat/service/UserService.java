package com.eat.service;

import com.eat.dto.request.PreferenceUpdateRequest;
import com.eat.dto.response.LoginResponse;

import java.util.Map;

public interface UserService {

    void sendCode(String phone);

    LoginResponse login(String phone, String code);

    Map<String, Object> getProfile(Long userId);

    Map<String, Object> getPreference(Long userId);

    void updatePreference(Long userId, PreferenceUpdateRequest request);
}
