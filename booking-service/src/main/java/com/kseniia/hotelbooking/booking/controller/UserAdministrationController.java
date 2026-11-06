package com.kseniia.hotelbooking.booking.controller;

import com.kseniia.hotelbooking.booking.dto.CreateUserRequest;
import com.kseniia.hotelbooking.booking.dto.DeleteUserRequest;
import com.kseniia.hotelbooking.booking.dto.UpdateUserRequest;
import com.kseniia.hotelbooking.booking.dto.UserResponse;
import com.kseniia.hotelbooking.booking.service.UserAdministrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

    @PatchMapping
    @Operation(
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "200", description = "User updated")
    )
    public UserResponse update(@Valid @RequestBody UpdateUserRequest request) {
        return userAdministrationService.update(request);
    }

    @DeleteMapping
    @Operation(
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "User and bookings deleted"),
                    @ApiResponse(responseCode = "400", description = "Invalid request"),
                    @ApiResponse(responseCode = "401", description = "Authentication required"),
                    @ApiResponse(responseCode = "403", description = "Administrator role required"),
                    @ApiResponse(responseCode = "404", description = "User not found"),
                    @ApiResponse(responseCode = "409", description = "User has a pending booking"),
                    @ApiResponse(responseCode = "503", description = "Hotel service unavailable")
            }
    )
    public ResponseEntity<Void> delete(
            @Valid @RequestBody DeleteUserRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    ) {
        userAdministrationService.delete(request, authorization);
        return ResponseEntity.noContent().build();
    }
}
