package com.atm.service;

import com.atm.exception.ATMException;
import com.atm.model.BankAccount;
import com.atm.model.Customer;
import com.atm.model.Transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ATMService {
    private final Map<String, Customer> customersByAccountNumber;

    public ATMService(Map<String, Customer> customersByAccountNumber) {
        this.customersByAccountNumber = customersByAccountNumber;
    }

    public BigDecimal checkBalance(Customer customer) {
        return customer.getAccount().getBalance();
    }

    public void deposit(Customer customer, BigDecimal amount) {
        customer.getAccount().deposit(amount);
    }

    public void withdraw(Customer customer, BigDecimal amount) {
        customer.getAccount().withdraw(amount);
    }

    public void transfer(Customer sourceCustomer, String targetAccountNumber, BigDecimal amount) {
        BankAccount source = sourceCustomer.getAccount();
        Customer targetCustomer = customersByAccountNumber.get(targetAccountNumber);
        if (targetCustomer == null) {
            throw new ATMException("Target account number was not found.");
        }
        if (source.getAccountNumber().equals(targetAccountNumber)) {
            throw new ATMException("Cannot transfer to the same account.");
        }

        // Debit first so a failed transfer never credits the target account without available source funds.
        source.debitForTransfer(amount, targetAccountNumber);
        targetCustomer.getAccount().creditFromTransfer(amount, source.getAccountNumber());
    }

    public List<Transaction> getTransactionHistory(Customer customer) {
        return customer.getAccount().getTransactions();
    }

    public List<Transaction> getMiniStatement(Customer customer) {
        return customer.getAccount().getMiniStatement(5);
    }

    public void changePin(Customer customer, String newPin) {
        customer.getAccount().changePin(newPin);
    }

    public List<Customer> getAllCustomers() {
        return new ArrayList<>(customersByAccountNumber.values());
    }

    public static Map<String, Customer> createDemoCustomers() {
        Map<String, Customer> customers = new HashMap<>();
        addCustomer(customers, new Customer("C001", "Asha Sharma",
                new com.atm.model.SavingsAccount("1001001001", "1234", new BigDecimal("25000.00"), new BigDecimal("10000.00"))));
        addCustomer(customers, new Customer("C002", "Ravi Kumar",
                new com.atm.model.CurrentAccount("1001001002", "2345", new BigDecimal("50000.00"), new BigDecimal("25000.00"))));
        addCustomer(customers, new Customer("C003", "Neha Patel",
                new com.atm.model.SavingsAccount("1001001003", "3456", new BigDecimal("15000.00"), new BigDecimal("10000.00"))));
        return customers;
    }

    private static void addCustomer(Map<String, Customer> customers, Customer customer) {
        customers.put(customer.getAccount().getAccountNumber(), customer);
    }
}
