package com.atm;

import com.atm.exception.ATMException;
import com.atm.exception.AuthenticationException;
import com.atm.model.Customer;
import com.atm.model.Transaction;
import com.atm.service.ATMService;
import com.atm.service.AuthenticationService;

import java.math.BigDecimal;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

public class ATM {
    private final Scanner scanner;
    private final AuthenticationService authenticationService;
    private final ATMService atmService;

    public ATM(Scanner scanner, AuthenticationService authenticationService, ATMService atmService) {
        this.scanner = scanner;
        this.authenticationService = authenticationService;
        this.atmService = atmService;
    }

    public static void main(String[] args) {
        Map<String, Customer> demoCustomers = ATMService.createDemoCustomers();
        ATMService atmService = new ATMService(demoCustomers);
        AuthenticationService authenticationService = new AuthenticationService(demoCustomers);
        new ATM(new Scanner(System.in), authenticationService, atmService).start();
    }

    public void start() {
        System.out.println("=====================================");
        System.out.println("       Welcome to Java 21 ATM");
        System.out.println("=====================================");

        while (true) {
            System.out.println("\nLogin as customer using account number, or admin using username 'admin'.");
            System.out.print("Account Number / Admin Username: ");
            String accountNumber = scanner.nextLine().trim();
            System.out.print("PIN: ");
            String pin = scanner.nextLine().trim();

            if ("exit".equalsIgnoreCase(accountNumber)) {
                System.out.println("Thank you for using Java 21 ATM.");
                break;
            }

            if (authenticationService.isAdminLogin(accountNumber, pin)) {
                showAdminMenu();
                continue;
            }

            try {
                Optional<Customer> customer = authenticationService.login(accountNumber, pin);
                if (customer.isPresent() && !showCustomerMenu(customer.get())) {
                    System.out.println("Thank you for using Java 21 ATM.");
                    break;
                }
            } catch (AuthenticationException exception) {
                System.out.println("Login failed: " + exception.getMessage());
            }
        }
    }

    private boolean showCustomerMenu(Customer customer) {
        System.out.println("\nHello, " + customer.getName() + "!");
        boolean active = true;
        while (active) {
            printCustomerMenu();
            int choice = readInt("Enter choice: ");
            try {
                switch (choice) {
                    case 1 -> System.out.printf("Available Balance: $%.2f%n", atmService.checkBalance(customer));
                    case 2 -> handleDeposit(customer);
                    case 3 -> handleWithdraw(customer);
                    case 4 -> handleTransfer(customer);
                    case 5 -> printTransactions("Transaction History", atmService.getTransactionHistory(customer));
                    case 6 -> handlePinChange(customer);
                    case 7 -> printTransactions("Mini Statement - Last 5 Transactions", atmService.getMiniStatement(customer));
                    case 8 -> {
                        active = false;
                        System.out.println("Exiting system...");
                    }
                    default -> System.out.println("Invalid menu choice.");
                }
            } catch (ATMException exception) {
                System.out.println("Error: " + exception.getMessage());
            }
        }
        return false;
    }

    private void printCustomerMenu() {
        System.out.println("\n----------- Customer Menu -----------");
        System.out.println("1. Check Account Balance");
        System.out.println("2. Deposit Money");
        System.out.println("3. Withdraw Money");
        System.out.println("4. Transfer Funds");
        System.out.println("5. View Transaction History");
        System.out.println("6. Change PIN");
        System.out.println("7. Generate Mini Statement");
        System.out.println("8. Exit System");
    }

    private void handleDeposit(Customer customer) {
        BigDecimal amount = readAmount("Enter deposit amount: ");
        atmService.deposit(customer, amount);
        System.out.println("Deposit successful.");
    }

    private void handleWithdraw(Customer customer) {
        BigDecimal amount = readAmount("Enter withdrawal amount: ");
        atmService.withdraw(customer, amount);
        System.out.println("Withdrawal successful.");
    }

    private void handleTransfer(Customer customer) {
        System.out.print("Enter target account number: ");
        String targetAccountNumber = scanner.nextLine().trim();
        BigDecimal amount = readAmount("Enter transfer amount: ");
        atmService.transfer(customer, targetAccountNumber, amount);
        System.out.println("Transfer successful.");
    }

    private void handlePinChange(Customer customer) {
        System.out.print("Enter new 4-digit PIN: ");
        String newPin = scanner.nextLine().trim();
        atmService.changePin(customer, newPin);
        System.out.println("PIN changed successfully.");
    }

    private void showAdminMenu() {
        boolean active = true;
        while (active) {
            System.out.println("\n------------- Admin Menu ------------");
            System.out.println("1. View All Accounts");
            System.out.println("2. Exit Admin");
            int choice = readInt("Enter choice: ");
            switch (choice) {
                case 1 -> printAllAccounts();
                case 2 -> {
                    active = false;
                    System.out.println("Admin logged out successfully.");
                }
                default -> System.out.println("Invalid menu choice.");
            }
        }
    }

    private void printAllAccounts() {
        System.out.println("\nCustomer ID | Name           | Account     | Type    | Balance     | Status");
        System.out.println("----------------------------------------------------------------------------");
        for (Customer customer : atmService.getAllCustomers()) {
            System.out.printf("%-11s | %-14s | %-11s | %-7s | $%10.2f | %s%n",
                    customer.getCustomerId(),
                    customer.getName(),
                    customer.getAccount().getAccountNumber(),
                    customer.getAccount().getAccountType(),
                    customer.getAccount().getBalance(),
                    customer.getAccount().isLocked() ? "LOCKED" : "ACTIVE");
        }
    }

    private void printTransactions(String title, List<Transaction> transactions) {
        System.out.println("\n" + title);
        System.out.println("----------------------------------------------------------------------------");
        if (transactions.isEmpty()) {
            System.out.println("No transactions available.");
            return;
        }
        transactions.forEach(System.out::println);
    }

    private int readInt(String prompt) {
        while (true) {
            try {
                System.out.print(prompt);
                int value = scanner.nextInt();
                scanner.nextLine();
                return value;
            } catch (InputMismatchException exception) {
                scanner.nextLine();
                System.out.println("Invalid input. Please enter a number.");
            }
        }
    }

    private BigDecimal readAmount(String prompt) {
        while (true) {
            try {
                System.out.print(prompt);
                return new BigDecimal(scanner.nextLine().trim());
            } catch (NumberFormatException exception) {
                System.out.println("Invalid amount. Please enter a valid decimal number.");
            }
        }
    }
}
