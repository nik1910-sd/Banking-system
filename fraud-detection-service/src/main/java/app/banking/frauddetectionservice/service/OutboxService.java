package app.banking.frauddetectionservice.service;

import app.banking.frauddetectionservice.event.OutboxEvent;
import app.banking.frauddetectionservice.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public void saveEvent(String topic, String eventKey, Object payload) {
        try {
            OutboxEvent event = new OutboxEvent();
            event.setTopic(topic);
            event.setEventKey(eventKey);
            // Convert the Java Object (e.g. TransactionInitiatedEvent) to JSON string
            event.setPayload(objectMapper.writeValueAsString(payload));

            repository.save(event);
            log.info("Saved outbox event for topic: {}", topic);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save outbox event", e);
        }
    }
}