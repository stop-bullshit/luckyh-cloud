package com.luckyh.cloud.inventory;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * 库存服务启动类。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.luckyh.cloud.inventory.mapper")
@ComponentScan(basePackages = {"com.luckyh.cloud.inventory", "com.luckyh.cloud.common"})
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
