package app.banking.transactionservice.service;

import app.banking.transactionservice.dto.TransferRequest;
import app.banking.transactionservice.dto.TransferResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransactionService {
    public TransferResponse transfer(TransferRequest request) {
    }

    public TransferResponse getTransaction(String transactionId) {
    }

    public List<TransferResponse> getTransactionHistory(String accountNumber) {
    }

    public TransferResponse verifyOTP(String transactionId, String otp) {
    }
}
