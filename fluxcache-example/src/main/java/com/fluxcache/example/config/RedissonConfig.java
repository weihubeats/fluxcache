package com.fluxcache.example.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ObjectUtils;

/**
 * @author : wh
 * @date : 2024/11/15 13:20
 * @description:
 */
@Configuration
public class RedissonConfig {

    @Value("${redis.host}")
    private String redisLoginHost;
    @Value("${redis.port}")
    private Integer redisLoginPort;
    @Value("${redis.password}")
    private String redisLoginPassword;

    @Bean
    public RedissonClient redissonClient() {
        return createRedis(redisLoginHost, redisLoginPort, redisLoginPassword);
    }

    private RedissonClient createRedis(String redisHost, Integer redisPort, String redisPassword) {
        Config config = new Config();
        SingleServerConfig singleServerConfig = config.useSingleServer();
        singleServerConfig.setAddress("redis://" + redisHost + ":" + redisPort + "");
        if (!ObjectUtils.isEmpty(redisPassword)) {
            singleServerConfig.setPassword(redisPassword);
        }
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        config.setCodec(new JsonJacksonCodec(objectMapper));
        return Redisson.create(config);
    }

}
