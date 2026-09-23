package app.banking.transactionservice.event;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class OutboxEvent {
    @Id
    private String id = UUID.randomUUID().toString();
    private String topic;
    private String eventKey;
    @Column(columnDefinition = "TEXT")
    private String payload;
    private boolean published = false;
    @CreationTimestamp
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
}
