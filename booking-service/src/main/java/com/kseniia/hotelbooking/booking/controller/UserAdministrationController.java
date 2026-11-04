package com.kseniia.hotelbooking.booking.controller;

import com.kseniia.hotelbooking.booking.dto.CreateUserRequest;
import com.kseniia.hotelbooking.booking.dto.UserResponse;
import com.kseniia.hotelbooking.booking.service.UserAdministrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserAdministrationController {

    private final UserAdministrationService userAdministrationService;

    public UserAdministrationController(UserAdministrationService userAdministrationService) {
        this.userAdministrationService = userAdministrationService;
    }

    @PostMapping
    @Operation(
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "201", description = "User created")
    )
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userAdministrationService.create(request));
    }
}
