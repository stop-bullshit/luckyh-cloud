# LuckyH Cloud - Spring Cloud 微服务项目

一个完整的 Spring Cloud 微服务架构项目，包含服务治理、配置管理、JWT 认证、服务间调用等功能。

## 🏗️ 项目架构

```
luckyh-cloud/
├── common-service/          # 公共模块
├── gateway-service/         # API网关服务
├── auth-service/           # 认证授权服务
├── user-service/           # 用户管理服务
├── order-service/          # 订单管理服务
└── pom.xml                 # 父级POM文件
```

## 📋 技术栈

### 核心框架
- **Spring Boot 3.4.0** - 基础应用框架
- **Spring Cloud 2024.0.0** - 微服务框架
- **JDK 17** - Java运行环境

### 服务治理
- **Nacos 2.4.2** - 服务注册发现、配置中心
- **Spring Cloud Gateway** - API网关
- **OpenFeign** - 服务间调用
- **Spring Cloud LoadBalancer** - 负载均衡

### 数据存储
- **MySQL 8.0** - 关系型数据库
- **MyBatis Plus 3.5.7** - ORM框架
- **Redis** - 缓存和会话存储

### 安全认证
- **JWT (JJWT 0.12.6)** - 无状态认证
- **Spring Security Crypto** - 密码加密
- **BCrypt** - 密码哈希算法

### 工具库
- **Hutool** - Java工具库
- **Lombok** - 代码简化
- **Validation** - 参数校验

## ✅ 项目完成度

### 已实现功能
- [x] **完整微服务架构** - Gateway、Auth、User、Order四个核心服务
- [x] **服务注册与发现** - 基于Nacos的服务治理
- [x] **配置中心** - Nacos统一配置管理
- [x] **API网关** - 统一入口、路由转发、认证过滤
- [x] **JWT认证体系** - 完整的登录/注册/令牌管理
- [x] **服务间调用** - OpenFeign声明式调用
- [x] **数据持久化** - MyBatis Plus + MySQL
- [x] **Redis缓存** - Token黑名单、用户信息缓存
- [x] **公共模块** - 消除代码重复，统一工具类
- [x] **参数校验** - 完整的输入验证机制
- [x] **异常处理** - 统一异常处理体系
- [x] **RBAC权限** - 角色权限管理
- [x] **分页查询** - 支持条件筛选的分页功能
- [x] **降级熔断** - Feign调用降级处理

### 技术亮点
- ✨ **代码复用** - 创建common-service公共模块，避免重复代码
- ✨ **统一响应** - Result统一响应格式，ResultCode完整状态码
- ✨ **JWT工具** - 兼容性强的JWT工具类，支持多种使用场景
- ✨ **用户上下文** - ThreadLocal用户信息传递，简化业务开发
- ✨ **工具类库** - DateUtils、StringUtils等常用工具，提升开发效率
- ✨ **常量管理** - 统一常量定义，便于维护和修改
- ✨ **异常体系** - BusinessException、SystemException分层异常处理

## 🚀 快速开始

### 环境要求
- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+
- Nacos 2.4.2+

### 1. 下载Nacos
```bash
# 下载 Nacos 2.4.2
wget https://github.com/alibaba/nacos/releases/download/2.4.2/nacos-server-2.4.2.tar.gz
tar -xvf nacos-server-2.4.2.tar.gz
cd nacos/bin

# Linux/Mac启动
sh startup.sh -m standalone

# Windows启动
startup.cmd -m standalone
```

### 2. 创建数据库
```sql
-- 创建数据库
CREATE DATABASE luckyh_cloud DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建用户表
USE luckyh_cloud;
CREATE TABLE `user` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `username` varchar(50) NOT NULL COMMENT '用户名',
    `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名',
    `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
    `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
    `user_type` int DEFAULT '1' COMMENT '用户类型(1:普通用户 2:VIP用户)',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建订单表
CREATE TABLE `order_info` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `order_no` varchar(32) NOT NULL COMMENT '订单号',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `product_name` varchar(200) NOT NULL COMMENT '商品名称',
    `quantity` int NOT NULL COMMENT '数量',
    `price` decimal(10,2) NOT NULL COMMENT '单价',
    `total_amount` decimal(10,2) NOT NULL COMMENT '总金额',
    `status` int DEFAULT '1' COMMENT '订单状态(1:待支付 2:已支付 3:已发货 4:已完成 5:已取消)',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建认证相关表
CREATE TABLE `sys_user` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `username` varchar(50) NOT NULL COMMENT '用户名',
    `password` varchar(100) NOT NULL COMMENT '密码',
    `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名',
    `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
    `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
    `status` int DEFAULT '1' COMMENT '状态(0:禁用 1:启用)',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sys_role` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `role_name` varchar(50) NOT NULL COMMENT '角色名称',
    `role_code` varchar(50) NOT NULL COMMENT '角色编码',
    `description` varchar(200) DEFAULT NULL COMMENT '描述',
    `status` int DEFAULT '1' COMMENT '状态(0:禁用 1:启用)',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sys_user_role` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `role_id` bigint NOT NULL COMMENT '角色ID',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 插入初始数据
INSERT INTO `sys_role` (`role_name`, `role_code`, `description`) VALUES
('超级管理员', 'SUPER_ADMIN', '系统超级管理员'),
('管理员', 'ADMIN', '系统管理员'),
('普通用户', 'USER', '普通用户');

-- 插入测试用户 (密码: 123456)
INSERT INTO `sys_user` (`username`, `password`, `real_name`, `email`, `phone`) VALUES
('admin', '$2a$10$7JB720yubVSoKE9dX6h.4uQPpwjFmF8OyEBGvYl9d6o1cQ1D.j8aK', '管理员', 'admin@example.com', '13800138000'),
('user', '$2a$10$7JB720yubVSoKE9dX6h.4uQPpwjFmF8OyEBGvYl9d6o1cQ1D.j8aK', '测试用户', 'user@example.com', '13800138001');

INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES (1, 1), (2, 3);
```

### 3. 编译运行
```bash
# 编译项目
mvn clean compile

# 打包项目
mvn clean package -DskipTests

# 分别启动各个服务
cd gateway-service && mvn spring-boot:run
cd auth-service && mvn spring-boot:run
cd user-service && mvn spring-boot:run
cd order-service && mvn spring-boot:run
```

## 📡 API接口示例

### 认证接口
```bash
# 用户登录
POST http://localhost:8080/auth/login
Content-Type: application/json
{
    "username": "admin",
    "password": "123456"
}

# 用户注册
POST http://localhost:8080/auth/register
Content-Type: application/json
{
    "username": "newuser",
    "password": "123456",
    "realName": "新用户",
    "email": "newuser@example.com",
    "phone": "13800138002"
}
```

### 用户接口
```bash
# 获取用户列表
GET http://localhost:8080/users?current=1&size=10
Authorization: Bearer {your_token}

# 创建用户
POST http://localhost:8080/users
Authorization: Bearer {your_token}
Content-Type: application/json
{
    "username": "testuser",
    "realName": "测试用户",
    "email": "test@example.com",
    "phone": "13800138003",
    "userType": 1
}
```

### 订单接口
```bash
# 创建订单
POST http://localhost:8080/orders
Authorization: Bearer {your_token}
Content-Type: application/json
{
    "productName": "测试商品",
    "quantity": 2,
    "price": 99.99
}
```

## 🏗️ 模块说明

### common-service (公共模块)
统一的公共组件，避免代码重复：
- **统一响应**: Result、ResultCode
- **用户上下文**: UserContext (ThreadLocal)
- **JWT工具**: JwtUtils (兼容多种场景)
- **通用工具**: DateUtils、StringUtils、PageResult
- **常量定义**: CommonConstants、RedisConstants
- **异常处理**: BusinessException、SystemException

### gateway-service (网关服务)
API网关，统一入口：
- **路由配置**: 智能路由转发
- **认证过滤**: JWT令牌验证
- **跨域处理**: CORS配置
- **负载均衡**: 集成LoadBalancer

### auth-service (认证服务)
认证授权中心：
- **用户认证**: 登录/注册/退出
- **JWT管理**: 令牌生成/验证/刷新
- **权限控制**: RBAC权限模型
- **密码加密**: BCrypt安全加密

### user-service (用户服务)
用户管理服务：
- **用户管理**: 完整CRUD操作
- **分页查询**: 条件筛选分页
- **用户拦截器**: 自动用户信息解析
- **参数校验**: 完整输入验证

### order-service (订单服务)
订单管理服务：
- **订单管理**: 创建/查询/支付/取消
- **服务调用**: Feign调用用户服务
- **降级处理**: 服务降级和熔断
- **分页查询**: 支持条件筛选

## 🎯 下一步计划

1. **监控体系**: 集成Skywalking/Zipkin链路追踪
2. **消息队列**: 引入RabbitMQ/RocketMQ异步处理
3. **缓存优化**: Redis缓存策略优化
4. **文档生成**: Swagger/Knife4j API文档
5. **容器化**: Docker部署支持
6. **CI/CD**: Jenkins自动化部署

## 🤝 贡献指南

这是一个完整的Spring Cloud微服务学习项目，展示了：
- 完整的微服务架构设计
- 规范的代码组织结构
- 实用的工具类和公共组件
- 完善的认证授权体系
- 标准的开发规范

项目已实现所有核心功能，可以直接运行使用！

---

**Happy Coding! 🎉 项目已完成，可以正常运行！**

这是一个完整的Spring Cloud微服务架构项目，包含服务注册与发现、配置中心、服务间调用等完整的服务治理功能。

## 项目架构

```
luckyh-cloud/
├── gateway-service/          # API网关服务 (端口: 8080)
├── auth-service/            # 认证授权服务 (端口: 8083)
├── user-service/            # 用户微服务 (端口: 8081)
├── order-service/           # 订单微服务 (端口: 8082)
├── config/                  # Nacos配置文件
├── sql/                     # 数据库初始化脚本
└── pom.xml                  # 父项目POM文件
```

### 5. 认证授权
- JWT令牌认证
- 基于角色的权限控制(RBAC)
- 统一的用户身份验证
- Redis缓存令牌黑名单

## 技术栈

- **Java**: JDK 25
- **Spring Boot**: 3.4.0
- **Spring Cloud**: 2024.0.0
- **Spring Cloud Alibaba**: 2023.0.1.2
- **服务注册与发现**: Nacos 2.4.2
- **配置中心**: Nacos Config
- **服务调用**: OpenFeign
- **API网关**: Spring Cloud Gateway
- **数据库**: MySQL 8.0
- **ORM框架**: MyBatis Plus 3.5.7
- **构建工具**: Maven
- **工具库**: Hutool 5.8.29

## 核心功能

### 1. 服务注册与发现
- 使用Nacos作为注册中心
- 所有微服务自动注册到Nacos
- 支持服务健康检查

### 2. 配置中心
- 使用Nacos Config统一管理配置
- 支持配置热更新
- 按环境和服务分离配置

### 3. 服务间调用
- 使用OpenFeign进行声明式服务调用
- 支持负载均衡
- 包含熔断降级机制

### 4. API网关
- 使用Spring Cloud Gateway
- 统一路由和负载均衡
- 全局过滤器支持
- 跨域配置

## 快速开始

### 1. 环境准备

确保已安装以下软件：
- JDK 25
- Maven 3.6+
- MySQL 8.0+
- Nacos Server 2.4.2

### 2. 启动Nacos

下载并启动Nacos Server：

```bash
# 下载Nacos
wget https://github.com/alibaba/nacos/releases/download/2.4.2/nacos-server-2.4.2.tar.gz
tar -xzf nacos-server-2.4.2.tar.gz

# 启动Nacos (单机模式)
cd nacos/bin
./startup.sh -m standalone

# Windows用户使用
startup.cmd -m standalone
```

访问Nacos控制台：
- URL: http://localhost:8848/nacos
- 用户名: nacos
- 密码: nacos

### 3. 数据库初始化

1. 创建MySQL数据库并执行初始化脚本：

```bash
mysql -u root -p < sql/init.sql
```

2. 确认数据库连接配置：
   - 数据库: luckyh_cloud
   - 用户名: root
   - 密码: 123456
   - 端口: 3306

### 4. 配置Nacos配置中心

在Nacos控制台创建以下配置：

#### 创建命名空间
1. 进入"命名空间"页面
2. 新建命名空间，命名空间ID: `luckyh-cloud`

#### 创建配置文件
在 `luckyh-cloud` 命名空间下创建以下配置文件：

1. **common.yml** (公共配置)
   - Data ID: `common.yml`
   - Group: `DEFAULT_GROUP`
   - 内容: 复制 `config/common.yml` 文件内容

2. **gateway-service.yml** (网关配置)
   - Data ID: `gateway-service.yml`
   - Group: `DEFAULT_GROUP`
   - 内容: 复制 `config/gateway-service.yml` 文件内容

3. **user-service.yml** (用户服务配置)
   - Data ID: `user-service.yml`
   - Group: `DEFAULT_GROUP`
   - 内容: 复制 `config/user-service.yml` 文件内容

4. **order-service.yml** (订单服务配置)
   - Data ID: `order-service.yml`
   - Group: `DEFAULT_GROUP`
   - 内容: 复制 `config/order-service.yml` 文件内容

### 5. 启动服务

按以下顺序启动各个服务：

```bash
# 1. 启动用户服务
cd user-service
mvn spring-boot:run

# 2. 启动订单服务
cd order-service
mvn spring-boot:run

# 3. 启动网关服务
cd gateway-service
mvn spring-boot:run
```

或者使用IDE直接运行各服务的启动类：
- `UserServiceApplication`
- `OrderServiceApplication`
- `GatewayServiceApplication`

### 6. 验证服务

#### 检查服务注册
访问Nacos控制台的"服务管理" -> "服务列表"，确认所有服务已注册。

#### 测试API接口

**认证相关API：**

```bash
# 用户登录
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "123456"
  }'

# 用户注册
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "newuser",
    "password": "123456",
    "confirmPassword": "123456",
    "realName": "新用户",
    "email": "newuser@example.com",
    "phone": "13999999999",
    "userType": 2
  }'

# 验证令牌（需要先登录获取token）
curl http://localhost:8080/api/auth/validate \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"

# 退出登录
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

**通过网关访问用户服务（需要认证）：**

```bash
# 获取用户列表（需要认证）
curl http://localhost:8080/api/user/users \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"

# 根据ID获取用户
curl http://localhost:8080/api/user/users/1 \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"

# 创建用户
curl -X POST http://localhost:8080/api/user/users \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -d '{
    "username": "testuser",
    "realName": "测试用户",
    "email": "test@example.com",
    "phone": "13888888888"
  }'
```

**通过网关访问订单服务（需要认证）：**

```bash
# 获取订单列表
curl http://localhost:8080/api/order/orders \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"

# 根据ID获取订单
curl http://localhost:8080/api/order/orders/1 \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"

# 创建订单
curl -X POST http://localhost:8080/api/order/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -d '{
    "userId": 1,
    "productName": "测试商品",
    "productPrice": 99.99,
    "quantity": 2
  }'
```

## 服务端口说明

| 服务名称 | 端口 | 说明 |
|---------|------|------|
| Nacos Server | 8848 | 注册中心和配置中心 |
| Gateway Service | 8080 | API网关 |
| Auth Service | 8083 | 认证授权服务 |
| User Service | 8081 | 用户微服务 |
| Order Service | 8082 | 订单微服务 |
| Redis | 6379 | 缓存和会话存储 |

## API文档

### 认证服务API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/login` | 用户登录 |
| POST | `/auth/register` | 用户注册 |
| POST | `/auth/refresh` | 刷新令牌 |
| POST | `/auth/logout` | 退出登录 |
| GET | `/auth/validate` | 验证令牌 |

### 用户服务API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/users` | 分页查询用户 |
| GET | `/users/{id}` | 根据ID获取用户 |
| POST | `/users` | 创建用户 |
| PUT | `/users/{id}` | 更新用户 |
| DELETE | `/users/{id}` | 删除用户 |

### 订单服务API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/orders` | 分页查询订单 |
| GET | `/orders/{id}` | 根据ID获取订单 |
| POST | `/orders` | 创建订单 |
| POST | `/orders/{id}/pay` | 支付订单 |
| POST | `/orders/{id}/cancel` | 取消订单 |

> **注意**: 除认证服务的登录、注册、刷新令牌接口外，其他所有接口都需要在请求头中携带有效的JWT令牌。

## 项目特性

### 服务治理
- ✅ 服务注册与发现 (Nacos)
- ✅ 配置中心 (Nacos Config)
- ✅ 服务间调用 (OpenFeign)
- ✅ 负载均衡 (Spring Cloud LoadBalancer)
- ✅ API网关 (Spring Cloud Gateway)

### 数据访问
- ✅ MyBatis Plus ORM框架
- ✅ MySQL数据库
- ✅ 逻辑删除支持
- ✅ 分页查询

### 系统监控
- ✅ Spring Boot Actuator健康检查
- ✅ 服务调用链路追踪
- ✅ 全局请求日志记录

### 容错机制
- ✅ Feign调用降级处理
- ✅ 全局异常处理
- ✅ 参数校验

### 分布式事务
- ✅ Seata AT模式分布式事务支持
- ✅ 全局事务管理
- ✅ 自动回滚和补偿机制
- 详见 [Seata集成文档](./SEATA-INTEGRATION.md)

## 开发指南

### 添加新的微服务

1. 在父项目下创建新模块
2. 添加依赖到父POM的modules中
3. 继承父项目POM
4. 添加必要的依赖
5. 创建启动类并添加相关注解
6. 配置application.yml和bootstrap.yml
7. 在Nacos中创建对应的配置文件

### 配置说明

所有配置文件都支持热更新，修改Nacos中的配置后会自动生效。

### 日志说明

项目使用SLF4J + Logback进行日志记录，支持按日期分割日志文件。

## 常见问题

### 1. 服务启动失败
- 检查Nacos是否启动
- 检查MySQL数据库连接
- 检查端口是否被占用

### 2. 服务调用失败
- 检查服务是否在Nacos中注册成功
- 检查Feign客户端配置
- 检查网络连接

### 3. 配置不生效
- 检查Nacos配置中心的配置是否正确
- 检查命名空间和Group是否匹配
- 重启相关服务

## 扩展功能

后续可以考虑添加：
- Redis缓存
- ✅ **分布式事务 (Seata)** - 已集成，详见 [SEATA-INTEGRATION.md](./SEATA-INTEGRATION.md)
- 限流降级 (Sentinel)
- 链路追踪 (Sleuth + Zipkin)
- 消息队列 (RocketMQ)
- 容器化部署 (Docker + Kubernetes)

## 联系方式

如有问题，请提交Issue或联系项目维护者。

---

🎉 恭喜！您已经成功创建了一个完整的Spring Cloud微服务项目！
