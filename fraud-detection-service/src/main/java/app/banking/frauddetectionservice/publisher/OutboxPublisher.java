package app.banking.frauddetectionservice.publisher;

import app.banking.frauddetectionservice.event.OutboxEvent;
import app.banking.frauddetectionservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedRate = 5000)
    public void publishEvents() {
        List<OutboxEvent> events = repository.findTop50ByPublishedFalseOrderByCreatedAtAsc();

        for (OutboxEvent event : events) {
            try {
                // .get() blocks to ensure Kafka actually received it
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload()).get();

                event.setPublished(true);
                event.setPublishedAt(LocalDateTime.now());
                repository.save(event);

                log.info("Successfully published outbox event to {}", event.getTopic());
            } catch (Exception e) {
                log.error("Kafka is down. Pausing outbox publisher. Will retry in 5 seconds.");
                break; // Stop loop to maintain strict ordering
            }
        }
    }
}