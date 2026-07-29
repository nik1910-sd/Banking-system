package app.banking.transactionservice.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransferRequest {

    @NotBlank(message = "Sender Account Number is required")
    private String senderAccountNumber;

    @NotBlank(message = "Receiver account number is required")
    private String receiverAccountNumber;

    @NotNull(message = "Amount is requires")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private String description;
}
