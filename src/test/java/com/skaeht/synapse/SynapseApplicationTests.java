package com.skaeht.synapse;

import com.skaeht.synapse.service.MessageBufferService;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class SynapseApplicationTests {

    @MockitoBean
    private RedissonClient redissonClient;

    @MockitoBean
    private MessageBufferService messageBufferService;

    @Test
    void contextLoads() {
        // Test passes if context successfully initializes without throwing an IllegalStateException
    }
}