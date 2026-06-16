package com.bank;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: verifies the Spring application context starts.
 *
 * <p>Runs against an in-memory H2 database (see {@code src/test/resources/application.yml})
 * so it does not require a running MySQL instance.
 */
@SpringBootTest
class BankingApplicationTests {

    @Test
    void contextLoads() {
        // Intentionally empty: succeeds if the application context loads without error.
    }
}
