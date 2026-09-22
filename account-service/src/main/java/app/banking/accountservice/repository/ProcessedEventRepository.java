package app.banking.accountservice.repository;

import app.banking.accountservice.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    boolean existsByEventKey(String eventKey);
}
