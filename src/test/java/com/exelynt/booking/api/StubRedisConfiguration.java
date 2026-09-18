package com.exelynt.booking.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Replaces the Redis client, not the blacklist.
 *
 * <p>Tests still run the real {@code RedisTokenBlacklist} — key naming, TTL
 * arithmetic and lookup are the production ones — against a map that honours
 * expiry the way Redis would. Only the network hop is stubbed, so the suite
 * needs no Redis server while the code under test is the code that ships.</p>
 */
@TestConfiguration
public class StubRedisConfiguration {

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public StringRedisTemplate stubStringRedisTemplate() {
        Map<String, Long> expiresAtMillis = new ConcurrentHashMap<>();
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Duration ttl = invocation.getArgument(2);
            expiresAtMillis.put(key, System.currentTimeMillis() + ttl.toMillis());
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        when(template.opsForValue()).thenReturn(valueOperations);
        when(template.hasKey(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Long expiry = expiresAtMillis.get(key);
            if (expiry == null) {
                return false;
            }
            if (expiry < System.currentTimeMillis()) {
                expiresAtMillis.remove(key);
                return false;
            }
            return true;
        });
        return template;
    }
}
