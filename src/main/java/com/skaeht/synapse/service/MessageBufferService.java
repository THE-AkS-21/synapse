package com.skaeht.synapse.service;

import com.skaeht.synapse.dto.event.ChatMessage;
import com.skaeht.synapse.entity.Message;
import com.skaeht.synapse.repository.MessageRepository;
import com.skaeht.synapse.repository.RoomRepository;
import com.skaeht.synapse.repository.UserRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ARCHITECTURE NOTE: High-Throughput Message Buffer
 * During chat spikes (e.g., thousands of users talking in a global room), direct DB writes
 * will exhaust connection pools and crash the database.
 * * This service implements the "Micro-batching" pattern. It absorbs incoming messages into an
 * in-memory bounded queue and flushes them to the DB in bulk (e.g., 100 at a time) or on a
 * timer tick, massively increasing throughput and protecting Postgres.
 */
@Slf4j
@Service
public class MessageBufferService {

    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    private final int maxBatchSize;
    private final long flushIntervalMs;

    // Bounded queue prevents OutOfMemory (OOM) errors if the database goes down completely
    private final BlockingQueue<ChatMessage> messageQueue;

    // Dedicated threads for scheduling the flush and executing the bulk insert
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

        // Daemon threads ensure they don't block the JVM from shutting down
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
    }

    public void bufferMessage(ChatMessage message) {
        if (messageQueue.offer(message)) {
            int currentPending = pendingMessages.incrementAndGet();
            // Trigger an immediate flush if we hit the batch threshold before the timer ticks
            if (currentPending >= maxBatchSize) {
                CompletableFuture.runAsync(this::flushBuffer, flusher);
            }
        } else {
            // Fallback strategy: If queue is at max capacity (10,000), bypass the buffer
            // and write directly to avoid losing data.
            log.warn("CRITICAL: Message queue is full. Bypassing buffer to write directly to DB.");
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

            // Bulk Insert: Massively faster than executing single INSERT statements
            messageRepository.saveAll(entities);
            log.debug("Flushed batch of {} messages to PostgreSQL", batch.size());

        } catch (Exception e) {
            log.error("Failed to flush messages to database, re-queuing...", e);
            // Re-queue ensures no data loss during temporary DB network blips
            batch.forEach(this::bufferMessage);
        }
    }

    /**
     * OPTIMIZATION NOTE:
     * Uses `getReferenceById()` instead of `findById()`. This generates a proxy object
     * representing the foreign key, entirely bypassing the need to execute a SELECT query
     * on the User or Room tables just to save the Message.
     */
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

    /**
     * Graceful Shutdown Hook.
     * Ensures any messages trapped in RAM are forced into the database before the app exits.
     */
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