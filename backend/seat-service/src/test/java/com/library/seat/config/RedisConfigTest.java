package com.library.seat.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RedisConfigTest {

    @Mock
    RedisConnectionFactory connectionFactory;

    private final RedisConfig config = new RedisConfig();

    @Test
    void redisTemplate_connectionFactoryIsSet() {
        RedisTemplate<String, Object> template = config.redisTemplate(connectionFactory);
        assertThat(template.getConnectionFactory()).isSameAs(connectionFactory);
    }

    @Test
    void redisTemplate_keySerializer_isStringRedisSerializer() {
        RedisTemplate<String, Object> template = config.redisTemplate(connectionFactory);
        assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
    }

    @Test
    void redisTemplate_valueSerializer_isJsonSerializer() {
        RedisTemplate<String, Object> template = config.redisTemplate(connectionFactory);
        assertThat(template.getValueSerializer()).isInstanceOf(GenericJackson2JsonRedisSerializer.class);
    }

    @Test
    void redisTemplate_hashKeySerializer_isStringRedisSerializer() {
        RedisTemplate<String, Object> template = config.redisTemplate(connectionFactory);
        assertThat(template.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);
    }

    @Test
    void redisTemplate_hashValueSerializer_isJsonSerializer() {
        RedisTemplate<String, Object> template = config.redisTemplate(connectionFactory);
        assertThat(template.getHashValueSerializer()).isInstanceOf(GenericJackson2JsonRedisSerializer.class);
    }
}
