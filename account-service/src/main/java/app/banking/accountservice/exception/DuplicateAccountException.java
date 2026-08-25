package app.banking.accountservice.exception;

public class DuplicateAccountException extends RuntimeException {

    public DuplicateAccountException(String email) {
        super("An account already exists for email: " + email);
    }
}
