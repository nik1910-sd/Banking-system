package app.banking.transactionservice.controller;


import app.banking.transactionservice.dto.TransferRequest;
import app.banking.transactionservice.dto.TransferResponse;
import app.banking.transactionservice.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/transactions")
@Slf4j
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/transfer")
    public ResponseEntity<TransferResponse> transfer(
           @Valid @RequestBody TransferRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.transfer(request));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransferResponse> getTransaction(
            @PathVariable String transactionId){
        return ResponseEntity.ok(transactionService.getTransaction(transactionId));
    }

    @GetMapping("/account/{accountNumber}")
    public ResponseEntity<List<TransferResponse>> getTransactionHistory(
            @PathVariable String accountNumber){
        return ResponseEntity.ok(transactionService.getTransactionHistory(accountNumber));
    }

    @PostMapping("/{transactionId}/verify")
    public ResponseEntity<TransferResponse> verifyOTP(
            @PathVariable String transactionId,
            @RequestParam String otp){

        return ResponseEntity.ok(transactionService.verifyOTP(transactionId,otp));
    }


}
