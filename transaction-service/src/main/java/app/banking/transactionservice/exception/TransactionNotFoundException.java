package app.banking.transactionservice.exception;

public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(String transactionId) {
        super("Transaction not found: " + transactionId);
    }
}
