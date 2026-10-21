package com.glimmer.common.config;

import io.lettuce.core.ClientOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 双 Redis 实例配置。
 *
 * <p><b>硬边界</b>：
 * <ul>
 *   <li>core：noeviction + AOF，只放锁 / Token / 秒杀资格 / 库存 / 布隆等不可丢数据；</li>
 *   <li>cache：allkeys-lru、不持久化，放详情 / Feed / 计数 / 限流等可丢缓存；</li>
 *   <li>两个实例之间禁止做多 Key 原子操作。</li>
 * </ul>
 *
 * <p>Spring Boot 的 RedisAutoConfiguration 已在启动类排除，所有连接显式创建，
 * 避免默认配置误连 localhost。
 */
@Configuration
public class RedisConfig {

    @Bean("coreRedisProperties")
    @ConfigurationProperties("glimmer.redis.core")
    public RedisNodeProperties coreRedisProperties() {
        return new RedisNodeProperties();
    }

    @Bean("cacheRedisProperties")
    @ConfigurationProperties("glimmer.redis.cache")
    public RedisNodeProperties cacheRedisProperties() {
        return new RedisNodeProperties();
    }

    @Bean("coreRedisConnectionFactory")
    public LettuceConnectionFactory coreRedisConnectionFactory(
            @Qualifier("coreRedisProperties") RedisNodeProperties properties) {
        return connectionFactory(properties);
    }

    @Bean("cacheRedisConnectionFactory")
    @Primary
    public LettuceConnectionFactory cacheRedisConnectionFactory(
            @Qualifier("cacheRedisProperties") RedisNodeProperties properties) {
        return connectionFactory(properties);
    }

    @Bean("coreRedisTemplate")
    public StringRedisTemplate coreRedisTemplate(
            @Qualifier("coreRedisConnectionFactory") LettuceConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean("cacheRedisTemplate")
    @Primary
    public StringRedisTemplate cacheRedisTemplate(
            @Qualifier("cacheRedisConnectionFactory") LettuceConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    /**
     * cache 实例的对象模板；需要存 DTO 时使用，避免调用方各自拼 JSON。
     */
    @Bean("cacheObjectRedisTemplate")
    public RedisTemplate<String, Object> cacheObjectRedisTemplate(
            @Qualifier("cacheRedisConnectionFactory") LettuceConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(RedisSerializer.string());
        template.setHashKeySerializer(RedisSerializer.string());
        GenericJackson2JsonRedisSerializer json = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(json);
        template.setHashValueSerializer(json);
        return template;
    }

    private LettuceConnectionFactory connectionFactory(RedisNodeProperties properties) {
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(
                properties.getHost(), properties.getPort());
        if (StringUtils.hasText(properties.getPassword())) {
            standalone.setPassword(RedisPassword.of(properties.getPassword()));
        }
        standalone.setDatabase(properties.getDatabase());

        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .clientOptions(ClientOptions.builder()
                        .autoReconnect(true)
                        .build())
                .commandTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .shutdownTimeout(Duration.ofMillis(200))
                .build();
        return new LettuceConnectionFactory(standalone, client);
    }
}
