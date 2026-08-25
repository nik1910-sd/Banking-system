package app.banking.accountservice.exception;

import app.banking.accountservice.entity.AccountStatus;

public class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException(String accountNumber, AccountStatus status) {
        super("Account " + accountNumber + " is not active - current status: " + status);
    }
}
