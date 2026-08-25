package app.banking.accountservice.service;

import app.banking.accountservice.repository.AccountRepository;
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
public class AccountEventConsumer{

    private final AccountService accountService;

    @KafkaListener(topics = "transaction.completed")
    public void consumeTransactionCompleted(
         @Payload Map<String, Object> payload){

     try{
         String receiverAccountNumber = (String) payload.get("receiverAccountNumber");
         BigDecimal amount = new BigDecimal( payload.get("amount").toString());

         log.info("Crediting account: {} amount: {}", receiverAccountNumber, amount);
         accountService.creditBalance(receiverAccountNumber, amount);

     }
     catch(Exception e){
         log.error(" Error crediting account: {}",e.getMessage());
     }
    }

    @KafkaListener(topics = "fraud.detected")
    public void consumeTransactionFailed(
         @Payload Map<String, Object> payload){

        try{
         String senderAccountNumber = (String) payload.get("senderAccountNumber");

         log.info("Fraud detected-blocking account: {} ", senderAccountNumber);
         accountService.blockAccount(senderAccountNumber);
        }

        catch (Exception e) {
            log.error("Error blocking account: {}",e.getMessage());
        }
    }

}
