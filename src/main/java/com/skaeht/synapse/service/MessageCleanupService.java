package com.skaeht.synapse.service;

import com.skaeht.synapse.repository.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class MessageCleanupService {

    private final MessageRepository messageRepository;

    public MessageCleanupService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    // This cron expression runs the job every day at midnight (00:00:00)
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void cleanupOldMessages() {
        long sevenDaysInMillis = 7L * 24 * 60 * 60 * 1000;
        long cutoffTimestamp = System.currentTimeMillis() - sevenDaysInMillis;

        long deletedCount = messageRepository.deleteByTimestampBefore(cutoffTimestamp);
        log.info("Deleted {} messages older than 7 days (timestamp: {})", deletedCount, cutoffTimestamp);
    }
}
