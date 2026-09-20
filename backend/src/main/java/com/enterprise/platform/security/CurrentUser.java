package com.enterprise.platform.security;

import java.util.UUID;

/** Principal object stored in the Spring SecurityContext for an authenticated request. */
public record CurrentUser(UUID userId, String email, String role) {
    public boolean isAdmin() { return "ADMIN".equals(role); }
}
