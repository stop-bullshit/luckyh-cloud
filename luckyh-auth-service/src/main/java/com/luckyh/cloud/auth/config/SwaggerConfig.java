package com.luckyh.cloud.auth.config;

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
    public OpenAPI authOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("认证服务API文档")
                        .description("用户认证授权相关接口")
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
