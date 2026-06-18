package com.bank.security;

import com.bank.domain.Role;
import com.bank.domain.UserAccount;
import com.bank.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Unit tests for {@link CustomUserDetailsService}: username lookup and authority mapping. */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserAccountRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    void mapsUserToUserDetailsWithRoleAuthority() {
        UserAccount user = new UserAccount("asha", "$2a$10$hash", Role.CUSTOMER);
        when(userRepository.findByUsername("asha")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("asha");

        assertThat(details.getUsername()).isEqualTo("asha");
        assertThat(details.getPassword()).isEqualTo("$2a$10$hash");
        assertThat(details.getAuthorities())
                .extracting("authority").containsExactly("ROLE_CUSTOMER");
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    void lockedUserIsReportedAsLocked() {
        UserAccount user = new UserAccount("ravi", "$2a$10$hash", Role.CUSTOMER);
        user.recordFailedLogin(1); // locks immediately at threshold 1
        when(userRepository.findByUsername("ravi")).thenReturn(Optional.of(user));

        assertThat(service.loadUserByUsername("ravi").isAccountNonLocked()).isFalse();
    }

    @Test
    void throwsWhenUserDoesNotExist() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
