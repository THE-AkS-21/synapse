package com.skaeht.synapse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import javax.sql.DataSource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import com.skaeht.synapse.repository.UserRepository;
import com.skaeht.synapse.repository.MessageRepository;
import com.skaeht.synapse.repository.RoomRepository;
import org.redisson.api.RedissonClient;

@SpringBootTest
@ActiveProfiles("test")
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        RedisAutoConfiguration.class
})
class SynapseApplicationTests {

    @MockitoBean private RedissonClient redissonClient;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private MessageRepository messageRepository;
    @MockitoBean private RoomRepository roomRepository;
    @MockitoBean private DataSource dataSource;
    @MockitoBean private RedisConnectionFactory redisConnectionFactory;
    @MockitoBean private ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;
    @MockitoBean private org.springframework.data.redis.listener.RedisMessageListenerContainer redisMessageListenerContainer;
    @MockitoBean private com.skaeht.synapse.repository.InvitationRepository invitationRepository;

    @MockitoBean private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;
    @MockitoBean private org.springframework.messaging.simp.SimpMessagingTemplate simpMessagingTemplate;

    @Test
    void contextLoads() {
    }
}