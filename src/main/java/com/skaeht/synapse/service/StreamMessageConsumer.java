package com.skaeht.synapse.service;

import com.skaeht.synapse.entity.Message;

import com.skaeht.synapse.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreamMessageConsumer {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MessageRepository messageRepository;

    private static final String STREAM = "messages:pending:stream";
    private static final String GROUP = "batch-processor";
    private static final String CONSUMER = "consumer-1";

    @Scheduled(fixedDelay = 2000)
    public void consumeMessages() {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                    Consumer.from(GROUP, CONSUMER),
                    StreamReadOptions.empty().count(100).block(Duration.ofSeconds(1)),
                    StreamOffset.create(STREAM, ReadOffset.lastConsumed()));

            if (records == null || records.isEmpty())
                return;

            List<Message> batch = new ArrayList<>();
            List<RecordId> recordIds = new ArrayList<>();

            for (MapRecord<String, Object, Object> record : records) {
                try {
                    Message message = new Message();
                    message.setMessageId((String) record.getValue().get("id"));
                    message.setRoomId((String) record.getValue().get("roomId"));
                    message.setContent((String) record.getValue().get("content"));
                    message.setSenderUsername((String) record.getValue().get("from"));
                    message.setTimestamp(Long.parseLong((String) record.getValue().get("timestamp")));

                    batch.add(message);
                    recordIds.add(record.getId());
                } catch (Exception e) {
                    log.error("Failed to parse message properties {}", record.getId(), e);
                }
            }

            if (!batch.isEmpty()) {
                try {
                    messageRepository.saveAll(batch);
                    log.info("Persisted {} messages to PostgreSQL", batch.size());

                    // Only acknowledge the consumed records AFTER safely putting them in the
                    // database!
                    redisTemplate.opsForStream().acknowledge(STREAM, GROUP, recordIds.toArray(new RecordId[0]));
                } catch (Exception e) {
                    log.error("Failed to persist message batch to database, aborting acknowledge...", e);
                }
            }
        } catch (Exception e) {
            log.debug("Redis connection unavailable for stream consumption (likely test environment or shutdown): {}",
                    e.getMessage());
        }
    }
}