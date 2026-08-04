package app.banking.frauddetectionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class FraudCheckResult {
   private boolean fraud;
   private String reason;
}
