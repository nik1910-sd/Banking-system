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
         String recieverAccountNumber = (String) payload.get("recieverAccountNumber");
         BigDecimal amount = new BigDecimal( payload.get("amount").toString());

         log.info("Crediting account: {} amount: {}", recieverAccountNumber, amount);
         accountService.creditBalance(recieverAccountNumber, amount);

     }
     catch(Exception e){
         log.info(" Error crediting account: {}",e.getMessage());
     }
    }

    @KafkaListener(topics = "fraud.detected")
    public void consumeTransactionFailed(
         @Payload Map<String, Object> payload){

        try{
         String recieverAccountNumber = (String) payload.get("recieverAccountNumber");

         log.info("Fraud detected-blocking account: {} ", recieverAccountNumber);
         accountService.blockAccount(recieverAccountNumber);
        }

        catch (Exception e) {
            log.info("Error crediting account: {}",e.getMessage());
        }
    }

}
