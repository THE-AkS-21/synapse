package com.skaeht.synapse;

import com.skaeht.synapse.service.MessageBufferService;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * ARCHITECTURE NOTE: Context Load Verification
 * This test acts as a "Smoke Test". It doesn't test business logic; it merely verifies
 * that the Spring Application Context can successfully wire together all beans, configurations,
 * and dependencies without throwing a fatal BeanCreationException on startup.
 */
@SpringBootTest
@ActiveProfiles("test")
class SynapseApplicationTests {

    /*
     * We explicitly mock heavy infrastructure clients here.
     * If we didn't mock RedissonClient, the Spring Context would attempt to establish
     * a physical TCP connection to Redis during the build process, which would cause
     * the CI/CD pipeline to crash if Redis isn't running in the test environment.
     */
    @MockitoBean
    private RedissonClient redissonClient;

    @MockitoBean
    private MessageBufferService messageBufferService;

    @Test
    void contextLoads() {
        // Test passes silently if the Spring Context successfully initializes.
    }
}