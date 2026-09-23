package app.banking.frauddetectionservice.repository;

import app.banking.frauddetectionservice.event.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findTop50ByPublishedFalseOrderByCreatedAtAsc();
}