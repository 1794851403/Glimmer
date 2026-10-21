package com.glimmer.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redisson 只连接 core 实例：锁与布隆过滤器不能放在会被 LRU 淘汰的 cache 实例。
 */
@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Qualifier("coreRedisProperties") RedisNodeProperties properties) {
        Config config = new Config();
        SingleServerConfig server = config.useSingleServer()
                .setAddress("redis://" + properties.getHost() + ":" + properties.getPort())
                .setDatabase(properties.getDatabase())
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(8)
                .setTimeout((int) Math.min(properties.getTimeoutMs(), Integer.MAX_VALUE))
                .setRetryAttempts(3)
                .setRetryInterval(1000);
        if (StringUtils.hasText(properties.getPassword())) {
            server.setPassword(properties.getPassword());
        }
        return Redisson.create(config);
    }
}
