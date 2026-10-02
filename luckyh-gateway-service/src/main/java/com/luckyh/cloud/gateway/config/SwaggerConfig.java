package com.luckyh.cloud.gateway.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Swagger配置 - Gateway聚合
 */
@Configuration
public class SwaggerConfig {

    /**
     * Gateway聚合所有服务的API文档
     */
    @Bean
    public List<GroupedOpenApi> apis(RouteDefinitionLocator locator) {
        List<GroupedOpenApi> groups = new ArrayList<>();

        // 获取Gateway路由定义
        List<RouteDefinition> definitions = locator.getRouteDefinitions().collectList().block();

        if (definitions != null) {
            definitions.stream()
                    .filter(routeDefinition -> routeDefinition.getId().matches(".*-service"))
                    .forEach(routeDefinition -> {
                        String name = routeDefinition.getId().replace("-service", "");
                        GroupedOpenApi.builder()
                                .pathsToMatch("/" + name + "/**")
                                .group(name)
                                .build();
                        groups.add(GroupedOpenApi.builder()
                                .pathsToMatch("/" + getServicePath(routeDefinition.getId()) + "/**")
                                .group(routeDefinition.getId())
                                .build());
                    });
        }

        return groups;
    }

    private String getServicePath(String serviceId) {
        // auth-service -> auth
        // user-service -> users
        // order-service -> orders
        switch (serviceId) {
            case "auth-service":
                return "auth";
            case "user-service":
                return "users";
            case "order-service":
                return "orders";
            default:
                return serviceId.replace("-service", "");
        }
    }
}
