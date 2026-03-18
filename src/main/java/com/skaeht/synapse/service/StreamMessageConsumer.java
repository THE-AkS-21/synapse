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
            log.debug("Consumed message from stream: {}", record.getId());
            Message message = new Message();

            message.setMessageId(record.getValue().get("id"));
            message.setRoom(roomRepository.getReferenceById(record.getValue().get("roomId")));
            message.setSender(userRepository.getReferenceById(Long.parseLong(record.getValue().get("senderId"))));
            message.setContent(record.getValue().get("content"));
            message.setTimestamp(Long.parseLong(record.getValue().get("timestamp")));
            message.setDeleted(false);

            messageRepository.save(message);

        } catch (Exception e) {
            log.error("Failed to process stream message: {}", record.getId(), e);
            // In a production app, you might want to implement a Dead Letter Queue (DLQ) here
        }
    }
}