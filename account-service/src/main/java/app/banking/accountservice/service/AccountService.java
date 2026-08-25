package app.banking.accountservice.service;

import app.banking.accountservice.dto.AccountResponse;
import app.banking.accountservice.dto.CreateAccountRequest;
import app.banking.accountservice.entity.Account;
import app.banking.accountservice.entity.AccountStatus;
import app.banking.accountservice.entity.AccountType;
import app.banking.accountservice.exception.AccountNotActiveException;
import app.banking.accountservice.exception.AccountNotFoundException;
import app.banking.accountservice.exception.DuplicateAccountException;
import app.banking.accountservice.exception.InsufficientBalanceException;
import app.banking.accountservice.repository.AccountRepository;
import jakarta.validation.Valid;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private static SecureRandom  random = new SecureRandom();

 //Generate unique 12 digit number
    private String generateAccountNumber() {
       String  accountNumber;

       do{
           long number=random.nextLong(1_000_000_000_000L);
           accountNumber=String.format("%012d",number);
       }while(accountRepository.existsByAccountNumber(accountNumber));

           return accountNumber;
    }


    private AccountResponse  mapToResponse(Account savedAccount) {
        AccountResponse response =AccountResponse.builder()
                .id(savedAccount.getId())
                .accountHolderName(savedAccount.getAccountHolderName())
                .accountNumber(savedAccount.getAccountNumber())
                .accountType(savedAccount.getAccountType())
                .email(savedAccount.getEmail())
                .phone(savedAccount.getPhone())
                .status(savedAccount.getStatus())
                .balance(savedAccount.getBalance())
                .dailyTransactionLimit(savedAccount.getDailyTransactionLimit())
                .createdAt(savedAccount.getCreatedAt())
                .build();

        return response;
    }


    public AccountResponse createAccount(@Valid CreateAccountRequest request) {
        log.info("Create account request for={}", request.getEmail());

        if(accountRepository.existsByEmail(request.getEmail())){
            throw new DuplicateAccountException(request.getEmail());
        }

        Account account = Account.builder()
                .accountHolderName(request.getAccountHolderName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .accountType(request.getAccountType())
                .balance(request.getInitialBalance())
                .status(AccountStatus.ACTIVE)
                .accountNumber(generateAccountNumber())
                .dailyTransactionLimit(
                        (request.getAccountType()== AccountType.SAVING)
                        ? new BigDecimal("1000000")
                        : new BigDecimal("5000000")
                )
                .build();

         Account savedAccount=accountRepository.save(account);

         log.info("Account Created: {}", savedAccount.getAccountNumber());



         return mapToResponse(savedAccount);
    }



    public AccountResponse getAccount(String accountNumber) {
        Account account=accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()->new AccountNotFoundException(accountNumber));

        return mapToResponse(account);
    }

    public BigDecimal getBalance(String accountNumber) {
        Account account=accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()->new AccountNotFoundException(accountNumber));

        return account.getBalance();
    }

    public void blockAccount(String accountNumber) {
        Account account=accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()->new AccountNotFoundException(accountNumber));
        account.setStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account blocked: {}", account.getAccountNumber());
    }

    public void deductBalance(String accountNumber, BigDecimal amount) {

        log.info("deduct balance {} from account: {} ", amount, accountNumber);

        Account account=accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()->new AccountNotFoundException(accountNumber));

        if(account.getStatus()!=AccountStatus.ACTIVE){
            throw new AccountNotActiveException(accountNumber, account.getStatus());
        }

        if(account.getBalance().compareTo(amount)<0){
            throw new InsufficientBalanceException(
                    accountNumber, account.getBalance(), amount);
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        log.info("Balance updated. New balance: {}", account.getBalance());
    }

    public void creditBalance(String accountNumber, BigDecimal amount) {
        log.info("credit balance {} from account: {} ", amount, accountNumber);
;
        Account account=accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()->new AccountNotFoundException(accountNumber));

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        log.info("Credit balance updated. New balance: {}", account.getBalance());
    }

}
