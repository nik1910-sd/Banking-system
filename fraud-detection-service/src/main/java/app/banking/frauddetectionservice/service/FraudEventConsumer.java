package app.banking.frauddetectionservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudEventConsumer {

   private final FraudDetectionService  service;

   @KafkaListener(
     topics = "transaction.initiated",
     groupId= "fraud-detection-group"
   )
   public void consumeTransactionInitiated(
           @Payload Map<String, Object> payload){
       log.info("Received transaction for fraud check: {}",
               payload.get("transactionId"));

       try{
          service.checkTransaction(payload);
       } catch (Exception e) {
           log.error("Error in fraud detection: {}", e.getMessage());
       }
   }

}
