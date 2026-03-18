package com.skaeht.synapse.service;

import com.skaeht.synapse.dto.event.ChatMessage;
import com.skaeht.synapse.entity.Message;
import com.skaeht.synapse.repository.MessageRepository;
import com.skaeht.synapse.repository.RoomRepository;
import com.skaeht.synapse.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class MessageBufferService {

    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    private final int maxBatchSize;
    private final long flushIntervalMs;

    private final BlockingQueue<ChatMessage> messageQueue;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService flusher;
    private final AtomicInteger pendingMessages = new AtomicInteger(0);

    public MessageBufferService(
            MessageRepository messageRepository,
            RoomRepository roomRepository,
            UserRepository userRepository,
            @Value("${chat.buffer.max-batch-size:100}") int maxBatchSize,
            @Value("${chat.buffer.flush-interval-ms:5000}") long flushIntervalMs) {

        this.messageRepository = messageRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;

        this.maxBatchSize = maxBatchSize;
        this.flushIntervalMs = flushIntervalMs;
        this.messageQueue = new LinkedBlockingQueue<>(10000);

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MsgBuffer-Scheduler");
            t.setDaemon(true);
            return t;
        });

        this.flusher = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "MsgBuffer-Flusher");
            t.setDaemon(true);
            return t;
        });

        this.scheduler.scheduleAtFixedRate(this::flushBuffer, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
        log.info("MessageBufferService initialized with maxBatchSize={}, flushIntervalMs={}", maxBatchSize, flushIntervalMs);
    }

    public void bufferMessage(ChatMessage message) {
        if (messageQueue.offer(message)) {
            int currentPending = pendingMessages.incrementAndGet();
            if (currentPending >= maxBatchSize) {
                CompletableFuture.runAsync(this::flushBuffer, flusher);
            }
        } else {
            log.warn("Message queue is full, writing directly to DB");
            messageRepository.save(convertToEntity(message));
        }
    }

    private synchronized void flushBuffer() {
        int messagesToFlush = pendingMessages.get();
        if (messagesToFlush == 0) return;

        List<ChatMessage> batch = new ArrayList<>(messagesToFlush);
        messageQueue.drainTo(batch, maxBatchSize);

        if (batch.isEmpty()) return;

        pendingMessages.addAndGet(-batch.size());

        try {
            List<Message> entities = batch.stream()
                    .map(this::convertToEntity)
                    .toList();

            messageRepository.saveAll(entities);
            log.debug("Flushed {} messages to database", batch.size());
        } catch (Exception e) {
            log.error("Failed to flush messages to database, re-queuing...", e);
            batch.forEach(this::bufferMessage);
        }
    }

    private Message convertToEntity(ChatMessage chatMessage) {
        return Message.builder()
                .messageId(chatMessage.getId())
                .room(roomRepository.getReferenceById(chatMessage.getRoomId()))
                .sender(userRepository.getReferenceById(chatMessage.getSenderId()))
                .content(chatMessage.getContent())
                .timestamp(chatMessage.getTimestamp())
                .isDeleted(false)
                .build();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down MessageBufferService, flushing remaining {} messages", pendingMessages.get());
        scheduler.shutdown();
        flushBuffer();
        flusher.shutdown();
        try {
            if (!flusher.awaitTermination(5, TimeUnit.SECONDS)) {
                flusher.shutdownNow();
            }
        } catch (InterruptedException e) {
            flusher.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}