package com.bank.domain;

/**
 * Authorization role for a {@link UserAccount}. Mapped to a Spring Security authority of the form
 * {@code ROLE_<name>} (e.g. {@code ROLE_CUSTOMER}).
 */
public enum Role {
    CUSTOMER,
    ADMIN;

    /** The Spring Security authority string for this role. */
    public String authority() {
        return "ROLE_" + name();
    }
}
