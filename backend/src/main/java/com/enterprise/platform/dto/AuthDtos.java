package com.enterprise.platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    public static class SignupRequest {
        @NotBlank @Email
        public String email;
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters")
        public String password;
    }

    public static class LoginRequest {
        @NotBlank @Email
        public String email;
        @NotBlank
        public String password;
    }

    public record AuthResponse(String token, String email, String role) {}
}
