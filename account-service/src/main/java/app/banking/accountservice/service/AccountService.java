package app.banking.accountservice.service;

import app.banking.accountservice.dto.AccountResponse;
import app.banking.accountservice.dto.CreateAccountRequest;
import app.banking.accountservice.entity.Account;
import app.banking.accountservice.entity.AccountStatus;
import app.banking.accountservice.entity.AccountType;
import app.banking.accountservice.entity.ProcessedEvent;
import app.banking.accountservice.repository.AccountRepository;
import app.banking.accountservice.repository.ProcessedEventRepository;
import jakarta.validation.Valid;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final ProcessedEventRepository processedEventRepository;
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
            throw new RuntimeException("account already exists"+request.getEmail());
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
                .orElseThrow(()->new RuntimeException("Account not found"));

        return mapToResponse(account);
    }

    public BigDecimal getBalance(String accountNumber) {
        Account account=accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()->new RuntimeException("Account not found"));

        return account.getBalance();
    }

    public void verifyOwnership(String accountNumber, String requestingUserEmail) {
        if (requestingUserEmail == null) {
            return; // Internal service call (no header) — allow
        }

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        if (!account.getEmail().equals(requestingUserEmail)) {
            throw new RuntimeException("Access denied — you can only access your own account");
        }
    }

    @Transactional
    public void blockAccount(String accountNumber, String idempotencyKey) {
        if (!claimEvent(idempotencyKey, "ACCOUNT_BLOCK")) {
            log.info("Duplicate block ignored for key={}", idempotencyKey);
            return;
        }

        Account account=accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()->new RuntimeException("Account not found"));
        account.setStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account blocked: {}", account.getAccountNumber());
    }

    @Transactional
    public void deductBalance(String accountNumber, BigDecimal amount, String idempotencyKey) {

        log.info("deduct balance {} from account: {} ", amount, accountNumber);


        // IDEMPOTENCY CHECK — prevent double-deduction on Feign retry
        if (!claimEvent(idempotencyKey, "ACCOUNT_DEBIT")) {
            log.info("Duplicate debit ignored for key={}", idempotencyKey);
            return;
        }

        Account account=accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(()->new RuntimeException("Account not found"));

        if(account.getStatus()!=AccountStatus.ACTIVE){
            throw new RuntimeException("account not active");
        }

        if(account.getBalance().compareTo(amount)<0){
            throw new RuntimeException("account balance not enough");
        }


        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        log.info("Balance updated. New balance: {}", account.getBalance());
    }

    @Transactional
    public void creditBalance(String accountNumber, BigDecimal amount, String idempotencyKey) {
        log.info("credit balance {} to account: {} key={}", amount, accountNumber, idempotencyKey);

        if (!claimEvent(idempotencyKey, "ACCOUNT_CREDIT")) {
            log.info("Duplicate credit ignored for key={}", idempotencyKey);
            return;
        }

        Account account=accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()->new RuntimeException("Account not found"));

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        log.info("Credit balance updated. New balance: {}", account.getBalance());
    }

    /**
     * Insert the event key in the SAME DB transaction as the money change.
     * If Kafka redelivers after a crash (offset not committed), the unique
     * constraint fails and we skip — no second credit.
     * If the credit itself fails, this insert rolls back so Kafka can retry.
     */
    private boolean claimEvent(String idempotencyKey, String eventType) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return true;
        }
        if (processedEventRepository.existsByEventKey(idempotencyKey)) {
            return false;
        }
        try {
            processedEventRepository.saveAndFlush(ProcessedEvent.builder()
                    .eventKey(idempotencyKey)
                    .eventType(eventType)
                    .build());
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            log.info("Lost the race claiming key={}, treating as duplicate", idempotencyKey);
            return false;
        }
    }

}
