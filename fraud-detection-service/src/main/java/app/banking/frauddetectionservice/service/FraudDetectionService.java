package app.banking.frauddetectionservice.service;

import app.banking.frauddetectionservice.client.AccountServiceClient;
import app.banking.frauddetectionservice.dto.FraudCheckResult;
import app.banking.frauddetectionservice.engine.FraudDetectionEngine;
import app.banking.frauddetectionservice.event.TransactionCleanEvent;
import app.banking.frauddetectionservice.event.VerificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionService {

    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String,Object> kafkaTemplate;
    private final FraudDetectionEngine fraudDetectionEngine;


    private static final String VERIFICATION_REQUIRED_TOPIC = "verification.required";
    private static final String FRAUD_CHECK_CLEAN_RESULT_TOPIC = "fraud.check.clean";


    public void checkTransaction(Map<String, Object> payload) {
        String transactionId = (String) payload.get("transactionId");
        String accountNumber = (String) payload.get("senderAccountNumber");
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());

        BigDecimal senderBalance = accountServiceClient.getBalance(accountNumber);

        log.info("Checking transaction: {} account: {} amount: {} balance: {}",
                transactionId, accountNumber, amount, senderBalance);

        FraudCheckResult result= fraudDetectionEngine.performFraudChecks(accountNumber,amount,senderBalance);

        if(result.isFraud()){

            log.info("Suspicious activity detected - account: {} "+
                            "reason: {} - requesting OTP verification",
                    accountNumber, result.getReason());

            VerificationEvent  verificationEvent = new VerificationEvent();
             verificationEvent.setTransactionId(transactionId);
             verificationEvent.setAccountNumber(accountNumber);
             verificationEvent.setAmount(amount);
             verificationEvent.setReason(result.getReason());

             kafkaTemplate.send(VERIFICATION_REQUIRED_TOPIC, verificationEvent.getTransactionId(), verificationEvent);
        }

        else{
            log.info("Transaction clean");

            TransactionCleanEvent transactionCleanEvent = new TransactionCleanEvent();
            transactionCleanEvent.setTransactionId(transactionId);
            transactionCleanEvent.setFraud(false);
            transactionCleanEvent.setReason(null);

            kafkaTemplate.send(FRAUD_CHECK_CLEAN_RESULT_TOPIC,transactionCleanEvent.getTransactionId(), transactionCleanEvent);

        }

    }
}
