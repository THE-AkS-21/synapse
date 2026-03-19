package com.skaeht.synapse.service;

import com.skaeht.synapse.entity.Message;
import com.skaeht.synapse.repository.MessageRepository;
import com.skaeht.synapse.repository.RoomRepository;
import com.skaeht.synapse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * ARCHITECTURE NOTE: Native Spring Data Redis Stream Consumer
 * This acts as a highly resilient queue worker. Redis Streams (unlike Pub/Sub) guarantee delivery.
 * If the application crashes, the messages wait in the stream. When the app reboots,
 * this consumer resumes reading, preventing message loss during deployments or outages.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamMessageConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        try {
            Map<String, String> payload = record.getValue();

            // USES getReferenceById to prevent N+1 SELECT execution during high-throughput ingestion
            Message message = Message.builder()
                    .messageId(payload.get("id"))
                    .room(roomRepository.getReferenceById(payload.get("roomId")))
                    .sender(userRepository.getReferenceById(Long.parseLong(payload.get("senderId"))))
                    .content(payload.get("content"))
                    .timestamp(Long.parseLong(payload.get("timestamp")))
                    .isDeleted(false)
                    .build();

            messageRepository.save(message);

        } catch (Exception e) {
            // Note: In an enterprise system, failing here should trigger a DLQ (Dead Letter Queue)
            // push, otherwise the consumer group might stall or infinitely retry poison pills.
            log.error("Failed to persist stream message payload: {}", record.getId(), e);
        }
    }
}