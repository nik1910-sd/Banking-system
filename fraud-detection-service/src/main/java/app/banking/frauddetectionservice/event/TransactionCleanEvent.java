package app.banking.frauddetectionservice.event;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TransactionCleanEvent {

   String transactionId;
   String reason;
   Boolean fraud;
}
