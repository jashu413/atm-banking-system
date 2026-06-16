package com.bank.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A bank customer. Migrated from the console {@code Customer}.
 *
 * <p>Owns exactly one {@link BankAccount} (one-to-one), preserving the console model. The
 * account is the inverse side here and the FK lives on the {@code accounts} table; {@code Customer}
 * is the cascade root so persisting a customer persists its account and transactions.
 */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_code", nullable = false, unique = true, length = 20)
    private String customerCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 120)
    private String email;

    @Column(length = 20)
    private String phone;

    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY, optional = false)
    private BankAccount account;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    protected Customer() {
        // for JPA
    }

    public Customer(String customerCode, String name) {
        this.customerCode = customerCode;
        this.name = name;
    }

    /** Links a customer and account on both sides of the relationship. */
    public void assignAccount(BankAccount account) {
        this.account = account;
        account.setCustomer(this);
    }

    public Long getId() {
        return id;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public BankAccount getAccount() {
        return account;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
