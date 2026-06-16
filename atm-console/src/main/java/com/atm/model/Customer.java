package com.atm.model;

public class Customer {
    private final String customerId;
    private final String name;
    private final BankAccount account;

    public Customer(String customerId, String name, BankAccount account) {
        this.customerId = customerId;
        this.name = name;
        this.account = account;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getName() {
        return name;
    }

    public BankAccount getAccount() {
        return account;
    }
}
