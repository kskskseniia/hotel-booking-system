package com.kseniia.hotelbooking.booking.controller;

import com.kseniia.hotelbooking.booking.dto.CredentialsRequest;
import com.kseniia.hotelbooking.booking.dto.TokenResponse;
import com.kseniia.hotelbooking.booking.service.UserAuthenticationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserAuthenticationController {

    private final UserAuthenticationService authenticationService;

    public UserAuthenticationController(UserAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody CredentialsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authenticationService.register(request));
    }

    @PostMapping("/auth")
    public ResponseEntity<TokenResponse> authenticate(@Valid @RequestBody CredentialsRequest request) {
        return ResponseEntity.ok(authenticationService.authenticate(request));
    }
}
