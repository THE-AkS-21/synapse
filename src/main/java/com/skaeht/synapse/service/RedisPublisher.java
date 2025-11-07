package com.skaeht.synapse.service;

import com.skaeht.synapse.config.RedisConfig;
import com.skaeht.synapse.dto.ChatMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisPublisher {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public void publish(ChatMessage chatMessage) {
        // This sends the message to the topic defined in RedisConfig
        redisTemplate.convertAndSend(RedisConfig.CHAT_TOPIC, chatMessage);
    }
}