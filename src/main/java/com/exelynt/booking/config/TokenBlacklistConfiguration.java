package com.exelynt.booking.config;

import com.exelynt.booking.security.InMemoryTokenBlacklist;
import com.exelynt.booking.security.RedisTokenBlacklist;
import com.exelynt.booking.security.TokenBlacklist;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Chooses where revoked access-token ids are kept.
 *
 * <p>{@code app.security.token-blacklist} is {@code in-memory} by default, which
 * suits a single instance and the tests. Deployments running more than one
 * replica must set it to {@code redis} (the {@code redis-blacklist} profile does
 * that, and adds Redis to the readiness probe): otherwise a logout is only
 * honoured by the instance that handled it.</p>
 */
@Configuration
public class TokenBlacklistConfiguration {

    @Bean
    @ConditionalOnProperty(name = "app.security.token-blacklist", havingValue = "redis")
    public TokenBlacklist redisTokenBlacklist(StringRedisTemplate redisTemplate) {
        return new RedisTokenBlacklist(redisTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.token-blacklist", havingValue = "in-memory", matchIfMissing = true)
    public TokenBlacklist inMemoryTokenBlacklist() {
        return new InMemoryTokenBlacklist();
    }
}
