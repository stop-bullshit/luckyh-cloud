package com.luckyh.cloud.user.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger配置
 * 
 * @author luckyh
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI userOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("用户服务API文档")
                        .description("用户管理相关接口")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("LuckyH")
                                .email("luckyh@example.com")
                                .url("https://github.com/luckyh"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
