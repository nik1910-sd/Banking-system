package app.banking.transactionservice.service;

import app.banking.transactionservice.client.AccountServiceClient;
import app.banking.transactionservice.dto.TransferRequest;
import app.banking.transactionservice.dto.TransactionResponse;
import app.banking.transactionservice.entity.Transaction;
import app.banking.transactionservice.entity.TransactionStatus;
import app.banking.transactionservice.entity.TransactionType;
import app.banking.transactionservice.event.TransactionCompletedEvent;
import app.banking.transactionservice.event.TransactionInitiatedEvent;
import app.banking.transactionservice.exception.EventPublishException;
import app.banking.transactionservice.exception.TransactionNotFoundException;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;


    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";
    private static final String FRAUD_DETECTED_TOPIC = "fraud.detected";

    private static final long KAFKA_ACK_TIMEOUT_SECONDS = 10;


    public TransactionResponse transfer(TransferRequest request){

        log.info("SAGA START - Transfer: {} -> {} amount: {}",
                request.getSenderAccountNumber(),
                request.getReceiverAccountNumber(),
                request.getAmount());

        // SAGA STEP 1: Deduct from sender
        accountServiceClient.deductBalance(
                request.getSenderAccountNumber(),
                request.getAmount());

        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber(request.getSenderAccountNumber());
        transaction.setReceiverAccountNumber(request.getReceiverAccountNumber());
        transaction.setAmount(request.getAmount());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setDescription(request.getDescription());
        transaction.setReferenceNumber(UUID.randomUUID().toString());

        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Transaction saved as PROCESSING: {}", savedTransaction.getId());

        // SAGA STEP - 2: Publish for fraud check
        TransactionInitiatedEvent event = new TransactionInitiatedEvent(
                savedTransaction.getId(),
                savedTransaction.getSenderAccountNumber(),
                savedTransaction.getReceiverAccountNumber(),
                savedTransaction.getAmount(),
                savedTransaction.getDescription()
        );

        // The sender is already debited at this point, so a dropped publish would
        // strand the money in PROCESSING forever. Block on the broker ack and
        // roll the debit back if it never arrives.
        try{
            kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC, savedTransaction.getId(), event)
                    .get(KAFKA_ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        catch(InterruptedException e){
            Thread.currentThread().interrupt();
            rollBackUnstartedTransfer(savedTransaction, e);
        }
        catch(Exception e){
            rollBackUnstartedTransfer(savedTransaction, e);
        }

        log.info("SAGA STEP 2 - TransactionInitiatedEvent published: {}", savedTransaction.getId());

        return mapToResponse(savedTransaction);
    }

    /**
     * SAGA STEP 2 never reached the broker, so no fraud check will ever run for
     * this transfer. Refund the sender, mark it FAILED and surface the failure.
     */
    private void rollBackUnstartedTransfer(Transaction transaction, Exception cause){

        log.error("SAGA STEP 2 FAILED - could not publish {}: {}",
                transaction.getId(), cause.getMessage());

        compensate(transaction, TransactionStatus.FAILED,
                "Fraud check could not be started - " + cause.getMessage());

        throw new EventPublishException(
                "Transfer aborted before fraud check could start - amount refunded",
                cause);
    }

    public TransactionResponse getTransaction(String transactionId){
        return mapToResponse(transactionRepository
                .findById(transactionId)
                .orElseThrow(() ->  new TransactionNotFoundException(transactionId)));
    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber){

        return transactionRepository
                .findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public TransactionResponse verifyOTP(String transactionID, String otp){
        log.info("OTP verification for the transaction: {}", transactionID);

        Transaction transaction = transactionRepository.findById(transactionID)
                .orElseThrow(() -> new TransactionNotFoundException(transactionID));

        String otpKey = "verification:otp" + transactionID;
        String storedOtp = redisTemplate.opsForValue().get(otpKey);

        if(storedOtp == null){
            // OTP EXPIRED
            log.warn("OTP expired for transaction: {}", transactionID);
            compensateTransaction(transaction, "OTP expired - transaction cancelled and amount refunded");
            return mapToResponse(transaction);
        }

        if(!storedOtp.equals(otp)){
            // BLOCK ACCOUNT AND REFUND
            log.warn("Wrong OTP - blocking account and refunding: {}", transactionID);
            redisTemplate.delete(otpKey);
            blockAccountAndCompensate(transaction,
                    "Wrong OTP entered - transaction cancelled, "+
                            "account blocked for security");

            return mapToResponse(transaction);
        }

        // OTP correct - complete transaction
        log.info("OTP verified - completing transaction: {}", transactionID);
        redisTemplate.delete(otpKey);
        completeTransaction(transaction);
        return mapToResponse(transaction);
    }

    private void compensateTransaction(Transaction transaction, String reason) {
        compensate(transaction, TransactionStatus.FLAGGED, reason);
    }

    /**
     * Refunds the sender and parks the transaction in {@code finalStatus}.
     * FLAGGED is used when a fraud rule rejected the transfer; FAILED when the
     * saga could not run at all.
     */
    private void compensate(Transaction transaction,
                            TransactionStatus finalStatus,
                            String reason) {
        log.warn("SAGA COMPENSATION - refunding: {} amount: {}",
                transaction.getSenderAccountNumber(),
                transaction.getAmount());

        // CREDIT MONEY BACK TO SENDER SYNCHRONOUSLY
        accountServiceClient.creditBalance(
                transaction.getSenderAccountNumber(),
                transaction.getAmount());

        transaction.setStatus(finalStatus);
        transaction.setFailureReason(reason +
                " - SAGA Compensation executed, amount refunded at "+ LocalDateTime.now());

        transactionRepository.save(transaction);

        // PUBLISH refund event - Notification service will alert user
        Map<String, Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId", transaction.getId());
        refundEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
        refundEvent.put("amount", transaction.getAmount());
        refundEvent.put("reason", reason);

        publish(TRANSACTION_REFUNDED_TOPIC, transaction.getId(), refundEvent);

        log.info("SAGA COMPENSATION COMPLETE - {} refunded to  {}",
                transaction.getAmount(), transaction.getSenderAccountNumber());
    }

    private void blockAccountAndCompensate(Transaction transaction, String reason){

        // Publish fraud.detected -> Account Service will block account
        Map<String, Object> fraudEvent = new HashMap<>();
        fraudEvent.put("transactionId", transaction.getId());
        fraudEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
        fraudEvent.put("reason", reason);

        publish(FRAUD_DETECTED_TOPIC, transaction.getSenderAccountNumber(), fraudEvent);
        log.warn("fraud.detected published - account: {} will be blocked, Kindly contact to the bank",
                transaction.getSenderAccountNumber());

        // SAGA COMPENSATION - refund Sender
        compensateTransaction(transaction, reason);
    }

    private void completeTransaction(Transaction transaction){
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

        publish(TRANSACTION_COMPLETED_TOPIC, transaction.getId(), completedEvent);

        log.info("SAGA COMPLETE - Transaction {} completed",
                transaction.getId());
    }

    /**
     * Fire-and-forget publish for post-commit events. These cannot be rolled
     * back, so a failure is logged loudly rather than swallowed - a dropped
     * transaction.completed means the receiver is never credited.
     */
    private void publish(String topic, String key, Object event){
        kafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if(ex != null){
                        log.error("PUBLISH FAILED - topic: {} key: {} - downstream " +
                                "consumers will not see this event", topic, key, ex);
                    }
                    else{
                        log.debug("Published to {} partition {} offset {}", topic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    public void processCleanResult(String transactionID){

        Transaction transaction = transactionRepository.findById(transactionID)
                .orElseThrow(() -> new TransactionNotFoundException(transactionID));

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
