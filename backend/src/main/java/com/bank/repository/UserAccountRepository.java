package com.bank.repository;

import com.bank.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data repository for {@link UserAccount}; {@code findByUsername} backs authentication. */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsername(String username);
}
