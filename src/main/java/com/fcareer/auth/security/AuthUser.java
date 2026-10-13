package com.fcareer.auth.security;

public record AuthUser(Long id, String email, String role) {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    public AuthUser(Long id, String email) {
        this(id, email, ROLE_USER);
    }

    public boolean admin() {
        return ROLE_ADMIN.equals(role);
    }
}
