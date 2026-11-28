package com.carbonx.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Admin user provisioning and lifecycle management contracts. */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record AdminCreateUserRequest(
            @NotBlank @Email @Size(max = 191) String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Size(max = 191) String fullName,
            @NotBlank @Size(max = 32) String role
    ) {
    }

    public record UpdateUserStatusRequest(
            @NotBlank @Size(max = 32) String status
    ) {
    }

    public record AdminUserResponse(
            Long id,
            String email,
            String fullName,
            String role,
            String status,
            Instant createdAt
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank String oldPassword,
            @NotBlank @Size(min = 8, max = 128) String newPassword,
            @NotBlank String confirmPassword
    ) {
    }
}
