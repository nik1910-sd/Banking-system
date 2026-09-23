package app.banking.frauddetectionservice.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
