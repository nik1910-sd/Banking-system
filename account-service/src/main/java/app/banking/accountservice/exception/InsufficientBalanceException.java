package app.banking.accountservice.exception;

import java.math.BigDecimal;

public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(String accountNumber,
                                       BigDecimal balance,
                                       BigDecimal requested) {
        super("Insufficient balance in account " + accountNumber
                + " - available: " + balance + ", requested: " + requested);
    }
}
