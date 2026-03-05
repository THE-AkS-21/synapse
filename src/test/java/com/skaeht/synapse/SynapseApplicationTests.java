
package com.skaeht.synapse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import javax.sql.DataSource;
import com.skaeht.synapse.repository.UserRepository;
import com.skaeht.synapse.repository.MessageRepository;
import com.skaeht.synapse.repository.RoomRepository;
import org.redisson.api.RedissonClient;

@SpringBootTest
@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class, RedisAutoConfiguration.class })
class SynapseApplicationTests {

    @MockitoBean
    private RedissonClient redissonClient;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MessageRepository messageRepository;

    @MockitoBean
    private RoomRepository roomRepository;

    @MockitoBean
    private DataSource dataSource;

    @Test
    void contextLoads() {
    }

}