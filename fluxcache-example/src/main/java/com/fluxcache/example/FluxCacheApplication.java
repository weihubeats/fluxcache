package com.fluxcache.example;

import com.fluxcache.core.annotation.EnableFluxCaching;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author : wh
 * @date : 2024/11/16 16:13
 * @description:
 */
@SpringBootApplication
@EnableFluxCaching
public class FluxCacheApplication {

    public static void main(String[] args) {
        SpringApplication.run( FluxCacheApplication.class, args);
    }


}
