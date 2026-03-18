package com.skaeht.synapse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skaeht.synapse.dto.event.ChatMessage;
import com.skaeht.synapse.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

            // Uses getRoomParticipants instead of getParticipantIds
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

    public Map<String, Integer> getUnreadCountsForUser(Long userId) {
        Map<String, Integer> unreadCounts = new HashMap<>();
        String pattern = "unread:user:" + userId + ":room:*";

        Set<String> keys = stringRedisTemplate.keys(pattern);

        if (keys != null) {
            for (String key : keys) {
                String[] parts = key.split(":");
                if (parts.length == 5) {
                    String roomId = parts[4];
                    String countStr = stringRedisTemplate.opsForValue().get(key);
                    if (countStr != null) {
                        unreadCounts.put(roomId, Integer.parseInt(countStr));
                    }
                }
            }
        }
        return unreadCounts;
    }

    public void clearUnreadCount(Long userId, String roomId) {
        String unreadKey = "unread:user:" + userId + ":room:" + roomId;
        stringRedisTemplate.delete(unreadKey);
    }
}