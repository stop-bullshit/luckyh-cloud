# Nacos配置中心说明

## 配置文件列表

### 1. 共享配置
- **Data ID**: `common.yml`
- **Group**: `DEFAULT_GROUP`
- **描述**: 所有微服务共享的配置，包括数据库、Redis、RabbitMQ、SkyWalking等
- **使用服务**: 所有微服务

### 2. 网关服务配置
- **Data ID**: `gateway-service.yml`  
- **Group**: `DEFAULT_GROUP`
- **描述**: 网关路由配置、CORS配置、限流配置
- **使用服务**: gateway-service

### 3. 认证服务配置
- **Data ID**: `auth-service.yml`
- **Group**: `DEFAULT_GROUP`  
- **描述**: JWT配置、安全配置
- **使用服务**: auth-service

### 4. 用户服务配置
- **Data ID**: `user-service.yml`
- **Group**: `DEFAULT_GROUP`
- **描述**: 用户业务相关配置
- **使用服务**: user-service

### 5. 订单服务配置  
- **Data ID**: `order-service.yml`
- **Group**: `DEFAULT_GROUP`
- **描述**: 订单业务相关配置
- **使用服务**: order-service

## 配置中心部署步骤

### 1. 启动Nacos
```bash
# 下载Nacos 2.3.0
# 启动Nacos（单机模式）
sh startup.sh -m standalone
```

### 2. 登录Nacos控制台
- 访问: http://localhost:8848/nacos
- 用户名: nacos
- 密码: nacos

### 3. 创建命名空间
1. 进入命名空间管理
2. 新建命名空间
   - 命名空间ID: `luckyh-cloud`
   - 命名空间名: `LuckyH微服务`
   - 描述: `LuckyH微服务项目配置`

### 4. 创建配置文件
在`luckyh-cloud`命名空间下，按照上述配置文件列表，逐一创建配置：

1. **common.yml** - 复制`nacos-config/common.yml`内容
2. **gateway-service.yml** - 复制`nacos-config/gateway-service.yml`内容  
3. **auth-service.yml** - 复制`nacos-config/auth-service.yml`内容
4. **user-service.yml** - 复制`nacos-config/user-service.yml`内容
5. **order-service.yml** - 复制`nacos-config/order-service.yml`内容

### 5. 验证配置
启动各个微服务，检查日志确认配置加载成功。

## 配置优先级

1. **Nacos配置中心** (最高优先级)
2. **application.yml** (最低优先级)

服务启动时会自动从Nacos拉取配置并覆盖本地配置。

## 动态配置刷新

所有配置支持动态刷新，在Nacos控制台修改配置后，服务会自动重新加载新配置（无需重启服务）。

## 注意事项

1. 确保各服务的`application.yml`中正确配置了Nacos连接信息
2. 敏感信息（如数据库密码）建议使用Nacos的加密配置功能
3. 生产环境建议使用Nacos集群模式
4. 定期备份Nacos配置数据