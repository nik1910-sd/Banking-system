package app.banking.transactionservice.service;

import app.banking.transactionservice.client.AccountServiceClient;
import app.banking.transactionservice.dto.TransferRequest;
import app.banking.transactionservice.dto.TransactionResponse;
import app.banking.transactionservice.entity.Transaction;
import app.banking.transactionservice.entity.TransactionStatus;
import app.banking.transactionservice.entity.TransactionType;
import app.banking.transactionservice.event.TransactionCompletedEvent;
import app.banking.transactionservice.event.TransactionInitiatedEvent;
import app.banking.transactionservice.repository.TransactionRepository;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;

    private final OutboxService outboxService;
    private final RedisTemplate<String, String> redisTemplate;


    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";
    private static final String FRAUD_DETECTED_TOPIC = "fraud.detected";



    public TransactionResponse transfer(TransferRequest request){


        log.info("SAGA START - Transfer: {} -> {} amount: {}",
                request.getSenderAccountNumber(),
                request.getReceiverAccountNumber(),
                request.getAmount());


        // SAGA STEP 1: deduct the amount

        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber(request.getSenderAccountNumber());
        transaction.setReceiverAccountNumber(request.getReceiverAccountNumber());
        transaction.setAmount(request.getAmount());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setDescription(request.getDescription());
        transaction.setReferenceNumber(UUID.randomUUID().toString());

        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Transaction saved as PROCESSING: {}", savedTransaction.getId());


        try{
            accountServiceClient.deductBalance(
                    request.getSenderAccountNumber(),
                    request.getAmount(),
                    "transaction.debit:" + savedTransaction.getId());

            savedTransaction.setStatus(TransactionStatus.PROCESSING);
            transactionRepository.save(savedTransaction);

            // SAGA STEP - 2: Publish for fraud check
            TransactionInitiatedEvent event = new TransactionInitiatedEvent(
                    savedTransaction.getId(),
                    savedTransaction.getSenderAccountNumber(),
                    savedTransaction.getReceiverAccountNumber(),
                    savedTransaction.getAmount(),
                    savedTransaction.getDescription()
            );


            outboxService.saveEvent(TRANSACTION_INITIATED_TOPIC, savedTransaction.getId(), event);

            log.info("SAGA STEP 2 - TransactionInitiatedEvent published: {}", savedTransaction.getId());

        }

        catch(Exception e){
            // 5. CATCH EXCEPTION: Mark as FAILED if deduction (e.g. low balance) or network fails
            log.error("Failed to process transfer for transaction: {}", savedTransaction.getId(), e);

            savedTransaction.setStatus(TransactionStatus.FAILED);
            savedTransaction.setFailureReason("Failed to deduct balance: " + e.getMessage());
            transactionRepository.save(savedTransaction);

            // Re-throw the exception so the controller can return an HTTP error to the user
            throw new RuntimeException("Transaction failed: " + e.getMessage());

        }

        return mapToResponse(savedTransaction);

    }





    public TransactionResponse getTransaction(String transactionId){
        return mapToResponse(transactionRepository
                .findById(transactionId)
                .orElseThrow(() ->  new RuntimeException(
                        "Transaction not found: "+transactionId
                )));
    }




    public List<TransactionResponse> getTransactionHistory(String accountNumber){

        return transactionRepository
                .findAllByAccountNumber(accountNumber)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }




    public TransactionResponse verifyOTP(String transactionId, String otp){
        log.info("OTP verification for the transaction: {}", transactionId);

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException(
                        "Transaction not found "+transactionId
                ));

        if(transaction.getStatus() == TransactionStatus.COMPLETED
                || transaction.getStatus() == TransactionStatus.FLAGGED){
            log.info("Transaction {} already {}, skipping OTP",
                    transactionId, transaction.getStatus());
            return mapToResponse(transaction);
        }

        if(transaction.getStatus() != TransactionStatus.PENDING_VERIFICATION){
            throw new RuntimeException(
                    "Transaction is not waiting for OTP: "+transaction.getStatus());
        }

        String otpKey = "verification:otp:" + transactionId;
        String storedOtp = redisTemplate.opsForValue().get(otpKey);

        if(storedOtp == null){
            // OTP EXPIRED
            log.warn("OTP expired for transaction: {}", transactionId);
            compensateTransaction(transaction, "OTP expired - transaction cancelled and amount refunded");
            return mapToResponse(transaction);
        }

        if(!storedOtp.equals(otp)){
            // BLOCK ACCOUNT AND REFUND
            log.warn("Wrong OTP - blocking account and refunding: {}", transactionId);
            redisTemplate.delete(otpKey);
            blockAccountAndCompensate(transaction,
                    "Wrong OTP entered - transaction cancelled, "+
                            "account blocked for security");

            return mapToResponse(transaction);
        }

        // OTP correct - complete transaction
        log.info("OTP verified - completing transaction: {}", transactionId);
        redisTemplate.delete(otpKey);
        completeTransaction(transaction);
        return mapToResponse(transaction);
    }





    private void compensateTransaction(Transaction transaction, String reason) {

        if(transaction.getStatus() == TransactionStatus.FLAGGED
                || transaction.getStatus() == TransactionStatus.COMPLETED){
            log.info("Compensation already done for {}, status={}",
                    transaction.getId(), transaction.getStatus());
            return;
        }

        log.warn("SAGA COMPENSATION - refunding: {} amount: {}",
                transaction.getSenderAccountNumber(),
                transaction.getAmount());

        // CREDIT MONEY BACK TO SENDER SYNCHRONOUSLY
        accountServiceClient.creditBalance(
                transaction.getSenderAccountNumber(),
                transaction.getAmount(),
                "transaction.refund:" + transaction.getId());

        transaction.setStatus(TransactionStatus.FLAGGED);
        transaction.setFailureReason(reason +
                " - SAGA Compensation executed, amount refunded at "+ LocalDateTime.now());

        transactionRepository.save(transaction);

        // PUBLISH refund event - Notification service will alert user
        Map<String, Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId", transaction.getId());
        refundEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
        refundEvent.put("amount", transaction.getAmount());
        refundEvent.put("reason", reason);

        outboxService.saveEvent(TRANSACTION_REFUNDED_TOPIC, transaction.getId(), refundEvent);

        log.info("SAGA COMPENSATION COMPLETE - {} refunded to  {}",
                transaction.getAmount(), transaction.getSenderAccountNumber());
    }




    private void blockAccountAndCompensate(Transaction transaction, String reason){

        // Publish fraud.detected -> Account Service will block account
        Map<String, Object> fraudEvent = new HashMap<>();
        fraudEvent.put("transactionId", transaction.getId());
        fraudEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
        fraudEvent.put("reason", reason);

        outboxService.saveEvent(FRAUD_DETECTED_TOPIC, transaction.getSenderAccountNumber(), fraudEvent);
        log.warn("fraud.detected published - account: {} will be blocked, Kindly contact to the bank",
                transaction.getSenderAccountNumber());

        // SAGA COMPENSATION - refund Sender
        compensateTransaction(transaction, reason);
    }




    private void completeTransaction(Transaction transaction){
        if(transaction.getStatus() == TransactionStatus.COMPLETED){
            log.info("Transaction {} already COMPLETED - skip publish",
                    transaction.getId());
            return;
        }
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        TransactionCompletedEvent completedEvent = new TransactionCompletedEvent(
                transaction.getId(),
                transaction.getSenderAccountNumber(),
                transaction.getReceiverAccountNumber(),
                transaction.getAmount(),
                transaction.getDescription()
        );

        outboxService.saveEvent(TRANSACTION_COMPLETED_TOPIC, transaction.getId(), completedEvent);

        log.info("SAGA COMPLETE - Transaction {} completed",
                transaction.getId());
    }






    public void processCleanResult(String transactionID){

        Transaction transaction = transactionRepository.findById(transactionID)
                .orElseThrow(() -> new RuntimeException(
                        "Transaction not found "+transactionID
                ));

        if(transaction.getStatus() != TransactionStatus.PROCESSING){
            log.warn("Transaction {} not PROCESSING - skipping", transactionID);
            return;
        }

        completeTransaction(transaction);
    }




    private TransactionResponse mapToResponse(Transaction transaction){

        TransactionResponse response = new TransactionResponse();
        response.setId(transaction.getId());
        response.setSenderAccountNumber(
                transaction.getSenderAccountNumber());
        response.setReceiverAccountNumber(
                transaction.getReceiverAccountNumber());
        response.setAmount(transaction.getAmount());
        response.setType(transaction.getType());
        response.setStatus(transaction.getStatus());
        response.setDescription(transaction.getDescription());
        response.setReferenceNumber(transaction.getReferenceNumber());
        response.setFailureReason(transaction.getFailureReason());
        response.setCreatedAt(transaction.getCreatedAt());
        response.setCompletedAt(transaction.getCompletedAt());

        return response;
    }

}
