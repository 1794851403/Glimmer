package com.glimmer.common.config;

import lombok.Data;

/**
 * 单个 Redis 实例的连接参数。
 */
@Data
public class RedisNodeProperties {

    private String host = "127.0.0.1";
    private int port = 6379;
    private String password = "";
    private int database = 0;
    private long timeoutMs = 3000;
}
