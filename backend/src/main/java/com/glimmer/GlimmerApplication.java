package com.glimmer;

import com.glimmer.common.auth.JwtProperties;
import com.glimmer.common.config.CorsProperties;
import com.glimmer.common.ratelimit.RateLimitProperties;
import com.glimmer.common.storage.StorageProperties;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 模块化单体入口。
 *
 * <p>Redis 的自动装配被排除：项目有两个独立 Redis 实例（core / cache），
 * 连接工厂与 Template 在 {@code RedisConfig} 中显式创建，避免误连到默认 localhost。
 */
@SpringBootApplication(exclude = {
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class
})
@EnableConfigurationProperties({
        JwtProperties.class,
        CorsProperties.class,
        RateLimitProperties.class,
        StorageProperties.class
})
@MapperScan(basePackages = "com.glimmer", annotationClass = Mapper.class)
@EnableScheduling
public class GlimmerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GlimmerApplication.class, args);
    }
}
