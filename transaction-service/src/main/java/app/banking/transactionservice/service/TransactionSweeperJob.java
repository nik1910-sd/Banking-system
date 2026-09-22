package app.banking.transactionservice.service;

import app.banking.transactionservice.client.AccountServiceClient;
import app.banking.transactionservice.entity.Transaction;
import app.banking.transactionservice.entity.TransactionStatus;
import app.banking.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionSweeperJob {

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";

    /**
     * Runs every 2 minutes.
     * Sweeps transactions stuck in PENDING or PENDING_VERIFICATION
     * that are older than 5 minutes.
     */
    @Scheduled(fixedRate = 120_000)
    public void sweepExpiredTransactions() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        log.info("SWEEPER - scanning for expired transactions older than {}", cutoff);

        // 1. Sweep PENDING transactions (deduction may or may not have happened)
        List<Transaction> stalePending = transactionRepository
                .findByStatusAndCreatedAtBefore(TransactionStatus.PENDING, cutoff);

        for (Transaction txn : stalePending) {
            log.warn("SWEEPER - expiring stale PENDING transaction: {}", txn.getId());
            txn.setStatus(TransactionStatus.FAILED);
            txn.setFailureReason("Transaction expired — stuck in PENDING state. " +
                    "No deduction was made.");
            transactionRepository.save(txn);
        }

        // 2. Sweep PENDING_VERIFICATION transactions (OTP never submitted)
        List<Transaction> expiredOtp = transactionRepository
                .findByStatusAndCreatedAtBefore(TransactionStatus.PENDING_VERIFICATION, cutoff);

        for (Transaction txn : expiredOtp) {
            log.warn("SWEEPER - OTP expired, refunding transaction: {}", txn.getId());
            refundAndFlag(txn, "OTP expired — transaction auto-cancelled by sweeper");
        }

        // 3. Sweep PROCESSING transactions (fraud check never responded)
        List<Transaction> staleProcessing = transactionRepository
                .findByStatusAndCreatedAtBefore(TransactionStatus.PROCESSING, cutoff);

        for (Transaction txn : staleProcessing) {
            log.warn("SWEEPER - refunding stale PROCESSING transaction: {}", txn.getId());
            refundAndFlag(txn, "Transaction expired — fraud check never responded. " +
                    "Amount refunded by sweeper.");
        }

        if (stalePending.isEmpty() && expiredOtp.isEmpty() && staleProcessing.isEmpty()) {
            log.info("SWEEPER - no expired transactions found");
        }
    }

    private void refundAndFlag(Transaction txn, String reason) {
        try {
            // Refund the sender
            accountServiceClient.creditBalance(
                    txn.getSenderAccountNumber(),
                    txn.getAmount(),
                    "sweeper.refund:" + txn.getId());

            txn.setStatus(TransactionStatus.FLAGGED);
            txn.setFailureReason(reason + " | Refunded at " + LocalDateTime.now());
            transactionRepository.save(txn);

            // Publish refund event so notification-service can alert the user
            Map<String, Object> refundEvent = new HashMap<>();
            refundEvent.put("transactionId", txn.getId());
            refundEvent.put("senderAccountNumber", txn.getSenderAccountNumber());
            refundEvent.put("amount", txn.getAmount());
            refundEvent.put("reason", reason);
            kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC, txn.getId(), refundEvent);

            log.info("SWEEPER - refund complete for transaction: {}", txn.getId());

        } catch (Exception e) {
            log.error("SWEEPER - failed to refund transaction: {}. Will retry next cycle.",
                    txn.getId(), e);
            // Don't update the status — leave it so the next sweep cycle retries
        }
    }
}