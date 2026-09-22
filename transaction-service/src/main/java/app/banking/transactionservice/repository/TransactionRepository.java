package app.banking.transactionservice.repository;

import app.banking.transactionservice.entity.Transaction;
import app.banking.transactionservice.entity.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction,String> {

    @Query("SELECT t FROM Transaction t " +
            "WHERE (t.senderAccountNumber = :accountNumber) " +
            "OR (t.receiverAccountNumber = :accountNumber AND t.status = 'COMPLETED') " +
            "ORDER BY t.createdAt DESC")
    List<Transaction> findAllByAccountNumber(@Param("accountNumber") String accountNumber);

    List<Transaction> findByStatusAndCreatedAtBefore(
            TransactionStatus status, LocalDateTime cutoff);

}
