package com.bank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the full stack banking application backend.
 *
 * <p>Phase 0 scaffolding only: the package structure defined in
 * {@code docs/migration-plan.md} is in place, but no domain logic has been
 * migrated from the console application yet.
 */
@SpringBootApplication
public class BankingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankingApplication.class, args);
    }
}
