package com.skaeht.synapse.service;

import com.skaeht.synapse.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * ARCHITECTURE NOTE: Data Retention Policy
 * Transient chat applications accumulate millions of rows quickly, degrading query performance.
 * This service implements a hard data retention policy, automatically purging historical
 * data to keep the database footprint lean and ensure GDPR/Privacy compliance.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MessageCleanupService {

    private final MessageRepository messageRepository;

    /**
     * Executes daily at 00:00:00 server time.
     * Deletes all messages older than 7 days.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void cleanupOldMessages() {
        long cutoffTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);

        long deletedCount = messageRepository.deleteByTimestampBefore(cutoffTimestamp);
        log.info("Retention Policy Executed: Purged {} messages older than 7 days.", deletedCount);
    }
}