package app.banking.transactionservice.dto;


import app.banking.transactionservice.entity.TransactionStatus;
import app.banking.transactionservice.entity.TransactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {


    private String id;

    private String senderAccountNumber;

    private String receiverAccountNumber;

    private BigDecimal amount;

    private TransactionType type;

    private TransactionStatus status;

    private String description;

    private String failureReason;

    private String referenceNumber;

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
}
