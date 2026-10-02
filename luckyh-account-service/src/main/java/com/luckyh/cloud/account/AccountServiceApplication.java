package com.luckyh.cloud.account;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * 账户服务启动类。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.luckyh.cloud.account.mapper")
@ComponentScan(basePackages = { "com.luckyh.cloud.account", "com.luckyh.cloud.common" })
public class AccountServiceApplication {

    /**
     * 启动账户服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
