package com.bookeatinglion.api.controller;

import com.bookeatinglion.common.response.ApiResponse;
import com.bookeatinglion.member.dto.AuthDto;
import com.bookeatinglion.member.service.AuthCommandService;
import com.bookeatinglion.member.service.AuthQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthCommandService authCommandService;
    private final AuthQueryService authQueryService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthDto.SignupResponse>> signup(
            @Valid @RequestBody AuthDto.SignupRequest request) {
        AuthDto.SignupResponse response = authCommandService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthDto.TokenResponse>> login(
            @Valid @RequestBody AuthDto.LoginRequest request) {
        AuthDto.TokenResponse response = authQueryService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthDto.TokenResponse>> refresh(
            @Valid @RequestBody AuthDto.RefreshRequest request) {
        AuthDto.TokenResponse response = authQueryService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
