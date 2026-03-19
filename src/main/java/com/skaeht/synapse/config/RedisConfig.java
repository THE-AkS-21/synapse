package com.skaeht.synapse.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.jackson2.CoreJackson2Module;

/**
 * ARCHITECTURE NOTE: Distributed Cache & Event Bus Configuration
 * Configures the primary RedisTemplate used across the application for both
 * Object caching (UserDetails) and Pub/Sub messaging.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> objectRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        /*
         * SERIALIZATION NOTE:
         * To safely cache complex Spring Security objects (like UserDetailsImpl containing
         * SimpleGrantedAuthority collections), we must register CoreJackson2Module.
         * DefaultTyping is activated so the JSON payload includes class metadata (@class),
         * allowing Jackson to reconstruct the exact Java types upon deserialization.
         */
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new CoreJackson2Module());
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}