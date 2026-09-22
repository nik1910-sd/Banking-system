package app.banking.accountservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * Unique per business action, e.g. transaction.completed:{transactionId}.
     * The unique constraint is what makes Kafka redelivery safe.
     */
    @Column(name = "event_key", nullable = false, unique = true, length = 191)
    private String eventKey;

    @Column(nullable = false)
    private String eventType;

    @CreationTimestamp
    private LocalDateTime processedAt;
}
