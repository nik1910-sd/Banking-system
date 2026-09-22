package app.banking.accountservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountEventConsumer {

    private final AccountService accountService;

    @KafkaListener(topics = "transaction.completed")
    public void consumeTransactionCompleted(
            @Payload Map<String, Object> payload) {

        String transactionId = String.valueOf(payload.get("transactionId"));
        String receiverAccountNumber = (String) payload.get("receiverAccountNumber");
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());
        String eventKey = "transaction.completed:" + transactionId;

        log.info("Crediting account: {} amount: {} eventKey: {}",
                receiverAccountNumber, amount, eventKey);

        // If this throws, Spring Kafka will NOT commit the offset and will retry.
        // If it succeeds, a later redelivery hits processed_events and is a no-op.
        accountService.creditBalance(receiverAccountNumber, amount, eventKey);
    }

    @KafkaListener(topics = "fraud.detected")
    public void consumeTransactionFailed(
            @Payload Map<String, Object> payload) {

        String transactionId = payload.get("transactionId") == null
                ? null
                : String.valueOf(payload.get("transactionId"));
        String senderAccountNumber = (String) payload.get("senderAccountNumber");
        String eventKey = transactionId == null
                ? null
                : "fraud.detected:" + transactionId;

        log.info("Fraud detected-blocking account: {} eventKey: {}",
                senderAccountNumber, eventKey);
        accountService.blockAccount(senderAccountNumber, eventKey);
    }
}
