package com.enterprise.platform.security;

import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthUtil {
    private AuthUtil() {}

    public static CurrentUser current() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof CurrentUser user)) {
            throw new IllegalStateException("No authenticated user in context");
        }
        return user;
    }
}
