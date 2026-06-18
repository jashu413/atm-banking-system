package com.bank.security;

import com.bank.domain.UserAccount;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adapts a {@link UserAccount} to Spring Security's {@link UserDetails}. The account's
 * {@code accountLocked} / {@code enabled} flags drive the standard pre-authentication checks, so a
 * locked user is rejected before any password comparison.
 */
public class CustomUserDetails implements UserDetails {

    private final UserAccount user;

    public CustomUserDetails(UserAccount user) {
        this.user = user;
    }

    /** The wrapped domain user, for callers that need more than the {@link UserDetails} contract. */
    public UserAccount getUserAccount() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(user.getRole().authority()));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !user.isAccountLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }
}
