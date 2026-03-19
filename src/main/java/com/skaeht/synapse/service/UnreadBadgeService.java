package com.skaeht.synapse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skaeht.synapse.dto.event.ChatMessage;
import com.skaeht.synapse.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ARCHITECTURE NOTE: Unread Badging System
 * This service tracks transient state for UI notification badges.
 * * PERFORMANCE WARNING: Currently fetches `roomService.getRoomParticipants(roomId)` on every message.
 * In a room with 10,000 users, this causes a massive DB read and 10,000 sequential Redis INCR commands
 * per message. Future iterations should implement Redis Pipelines and cache participant ID lists.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnreadBadgeService implements MessageListener {

    private final RedisTemplate<String, String> stringRedisTemplate;
    private final RoomService roomService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            ChatMessage chatMsg = objectMapper.readValue(message.getBody(), ChatMessage.class);
            String roomId = chatMsg.getRoomId();
            Long senderId = chatMsg.getSenderId();

            List<User> participants = roomService.getRoomParticipants(roomId);

            for (User participant : participants) {
                if (!participant.getId().equals(senderId)) {
                    String unreadKey = "unread:user:" + participant.getId() + ":room:" + roomId;
                    stringRedisTemplate.opsForValue().increment(unreadKey);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process background unread badge event", e);
        }
    }

    /**
     * Retrieves all unread badges for a user across all their rooms.
     * Uses SCAN instead of KEYS to avoid blocking the single-threaded Redis event loop.
     */
    public Map<String, Integer> getUnreadCountsForUser(Long userId) {
        Map<String, Integer> unreadCounts = new HashMap<>();
        String pattern = "unread:user:" + userId + ":room:*";

        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();

        try (Cursor<byte[]> cursor = stringRedisTemplate.getConnectionFactory()
                .getConnection().scan(options)) {

            while (cursor.hasNext()) {
                String key = new String(cursor.next());
                String[] parts = key.split(":");

                if (parts.length == 5) {
                    String roomId = parts[4];
                    String countStr = stringRedisTemplate.opsForValue().get(key);
                    if (countStr != null) {
                        unreadCounts.put(roomId, Integer.parseInt(countStr));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to execute Redis SCAN for unread counts", e);
        }

        return unreadCounts;
    }

    public void clearUnreadCount(Long userId, String roomId) {
        stringRedisTemplate.delete("unread:user:" + userId + ":room:" + roomId);
    }
}