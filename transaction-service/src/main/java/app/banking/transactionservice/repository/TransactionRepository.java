package app.banking.transactionservice.repository;

import app.banking.transactionservice.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction,String> {


}
