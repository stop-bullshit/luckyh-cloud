# Seata 分布式事务集成文档

## 概述

本项目已集成阿里巴巴的 Seata 分布式事务框架，用于处理跨微服务的分布式事务场景。

## 版本信息

- **Seata**: 2.0.0
- **Spring Cloud Alibaba**: 2023.0.1.2
- **Spring Boot**: 3.4.0
- **Spring Cloud**: 2024.0.0

## 架构说明

Seata 采用 AT 模式（自动补偿模式），主要包含以下组件：

1. **TC (Transaction Coordinator)** - 事务协调器：维护全局和分支事务的状态，驱动全局事务提交或回滚
2. **TM (Transaction Manager)** - 事务管理器：定义全局事务的范围，开始全局事务、提交或回滚全局事务
3. **RM (Resource Manager)** - 资源管理器：管理分支事务处理的资源，与TC交谈以注册分支事务和报告分支事务的状态

## 安装步骤

### 1. 下载 Seata Server

从 [Seata Releases](https://github.com/seata/seata/releases) 下载 Seata Server 2.0.0 版本。

```bash
wget https://github.com/seata/seata/releases/download/v2.0.0/seata-server-2.0.0.zip
unzip seata-server-2.0.0.zip
cd seata
```

### 2. 配置 Seata Server

#### 2.1 配置注册中心（使用 Nacos）

编辑 `conf/application.yml`：

```yaml
server:
  port: 7091

spring:
  application:
    name: seata-server

logging:
  config: classpath:logback-spring.xml
  file:
    path: ${user.home}/logs/seata

console:
  user:
    username: seata
    password: seata

seata:
  config:
    type: nacos
    nacos:
      server-addr: 127.0.0.1:8848
      namespace: ""
      group: SEATA_GROUP
      username: nacos
      password: nacos
      data-id: seataServer.properties
  registry:
    type: nacos
    nacos:
      application: seata-server
      server-addr: 127.0.0.1:8848
      group: SEATA_GROUP
      namespace: ""
      username: nacos
      password: nacos
  store:
    mode: db
    db:
      datasource: druid
      db-type: mysql
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://127.0.0.1:3306/seata?rewriteBatchedStatements=true
      user: root
      password: root
      min-conn: 5
      max-conn: 100
      global-table: global_table
      branch-table: branch_table
      lock-table: lock_table
      distributed-lock-table: distributed_lock
      query-limit: 100
      max-wait: 5000
```

#### 2.2 创建 Seata 数据库

执行以下 SQL 创建 Seata Server 所需的数据库表：

```sql
-- 创建 seata 数据库
CREATE DATABASE IF NOT EXISTS `seata` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `seata`;

-- Global Table
DROP TABLE IF EXISTS `global_table`;
CREATE TABLE `global_table` (
  `xid` varchar(128) NOT NULL,
  `transaction_id` bigint DEFAULT NULL,
  `status` tinyint NOT NULL,
  `application_id` varchar(32) DEFAULT NULL,
  `transaction_service_group` varchar(32) DEFAULT NULL,
  `transaction_name` varchar(128) DEFAULT NULL,
  `timeout` int DEFAULT NULL,
  `begin_time` bigint DEFAULT NULL,
  `application_data` varchar(2000) DEFAULT NULL,
  `gmt_create` datetime DEFAULT NULL,
  `gmt_modified` datetime DEFAULT NULL,
  PRIMARY KEY (`xid`),
  KEY `idx_status_gmt_modified` (`status`,`gmt_modified`),
  KEY `idx_transaction_id` (`transaction_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Branch Table
DROP TABLE IF EXISTS `branch_table`;
CREATE TABLE `branch_table` (
  `branch_id` bigint NOT NULL,
  `xid` varchar(128) NOT NULL,
  `transaction_id` bigint DEFAULT NULL,
  `resource_group_id` varchar(32) DEFAULT NULL,
  `resource_id` varchar(256) DEFAULT NULL,
  `branch_type` varchar(8) DEFAULT NULL,
  `status` tinyint DEFAULT NULL,
  `client_id` varchar(64) DEFAULT NULL,
  `application_data` varchar(2000) DEFAULT NULL,
  `gmt_create` datetime(6) DEFAULT NULL,
  `gmt_modified` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`branch_id`),
  KEY `idx_xid` (`xid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Lock Table
DROP TABLE IF EXISTS `lock_table`;
CREATE TABLE `lock_table` (
  `row_key` varchar(128) NOT NULL,
  `xid` varchar(128) DEFAULT NULL,
  `transaction_id` bigint DEFAULT NULL,
  `branch_id` bigint NOT NULL,
  `resource_id` varchar(256) DEFAULT NULL,
  `table_name` varchar(32) DEFAULT NULL,
  `pk` varchar(36) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0:locked ,1:rollbacking',
  `gmt_create` datetime DEFAULT NULL,
  `gmt_modified` datetime DEFAULT NULL,
  PRIMARY KEY (`row_key`),
  KEY `idx_branch_id` (`branch_id`),
  KEY `idx_xid` (`xid`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Distributed Lock Table
DROP TABLE IF EXISTS `distributed_lock`;
CREATE TABLE `distributed_lock` (
  `lock_key` varchar(128) NOT NULL,
  `lock_value` varchar(128) NOT NULL,
  `expire` bigint DEFAULT NULL,
  PRIMARY KEY (`lock_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

#### 2.3 向 Nacos 配置中心添加配置

在 Nacos 配置中心添加以下配置（Data ID: `seataServer.properties`，Group: `SEATA_GROUP`）：

```properties
# 事务存储模式
store.mode=db
store.lock.mode=db
store.session.mode=db

# 数据库配置
store.db.datasource=druid
store.db.dbType=mysql
store.db.driverClassName=com.mysql.cj.jdbc.Driver
store.db.url=jdbc:mysql://127.0.0.1:3306/seata?rewriteBatchedStatements=true&serverTimezone=Asia/Shanghai
store.db.user=root
store.db.password=root
store.db.minConn=5
store.db.maxConn=100
store.db.globalTable=global_table
store.db.branchTable=branch_table
store.db.lockTable=lock_table
store.db.distributedLockTable=distributed_lock
store.db.queryLimit=100
store.db.maxWait=5000

# 事务规则配置
transport.type=TCP
transport.server=NIO
transport.heartbeat=true
transport.enableTmClientBatchSendRequest=false
transport.enableRmClientBatchSendRequest=true
transport.enableTcServerBatchSendResponse=false
transport.rpcRmRequestTimeout=30000
transport.rpcTmRequestTimeout=30000
transport.rpcTcRequestTimeout=30000
transport.threadFactory.bossThreadPrefix=NettyBoss
transport.threadFactory.workerThreadPrefix=NettyServerNIOWorker
transport.threadFactory.serverExecutorThreadPrefix=NettyServerBizHandler
transport.threadFactory.shareBossWorker=false
transport.threadFactory.clientSelectorThreadPrefix=NettyClientSelector
transport.threadFactory.clientSelectorThreadSize=1
transport.threadFactory.clientWorkerThreadPrefix=NettyClientWorkerThread
transport.shutdown.wait=3
transport.serialization=seata
transport.compressor=none

# 事务分组配置
service.vgroupMapping.default_tx_group=default
service.default.grouplist=127.0.0.1:8091
service.enableDegrade=false
service.disableGlobalTransaction=false

# 客户端配置
client.rm.asyncCommitBufferLimit=10000
client.rm.lock.retryInterval=10
client.rm.lock.retryTimes=30
client.rm.lock.retryPolicyBranchRollbackOnConflict=true
client.rm.reportRetryCount=5
client.rm.tableMetaCheckEnable=true
client.rm.tableMetaCheckerInterval=60000
client.rm.sqlParserType=druid
client.rm.reportSuccessEnable=false
client.rm.sagaBranchRegisterEnable=false
client.rm.sagaJsonParser=fastjson
client.rm.tccActionInterceptorOrder=-2147482648
client.tm.commitRetryCount=5
client.tm.rollbackRetryCount=5
client.tm.defaultGlobalTransactionTimeout=60000
client.tm.degradeCheck=false
client.tm.degradeCheckAllowTimes=10
client.tm.degradeCheckPeriod=2000
client.tm.interceptorOrder=-2147482648
client.undo.dataValidation=true
client.undo.logSerialization=jackson
client.undo.onlyCareUpdateColumns=true
server.undo.logSaveDays=7
server.undo.logDeletePeriod=86400000
client.undo.logTable=undo_log
client.undo.compress.enable=true
client.undo.compress.type=zip
client.undo.compress.threshold=64k
```

### 3. 创建业务数据库的 undo_log 表

在每个业务数据库中执行 `sql/seata-undo-log.sql` 脚本，创建 `undo_log` 表：

```bash
mysql -u root -p luckyh_cloud < sql/seata-undo-log.sql
```

### 4. 启动 Seata Server

```bash
cd seata
sh ./bin/seata-server.sh -p 8091 -h 127.0.0.1
```

或者在 Windows 上：

```cmd
cd seata
bin\seata-server.bat -p 8091 -h 127.0.0.1
```

## 项目配置

### 1. 依赖配置

项目已在以下模块中添加了 Seata 依赖：

- `order-service`
- `user-service`

依赖配置：

```xml
<!-- Seata -->
<dependency>
    <groupId>io.seata</groupId>
    <artifactId>seata-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-seata</artifactId>
</dependency>
```

### 2. 应用配置

在 `application.yml` 中已添加 Seata 配置：

```yaml
seata:
  enabled: true
  application-id: ${spring.application.name}
  tx-service-group: default_tx_group
  service:
    vgroup-mapping:
      default_tx_group: default
  registry:
    type: nacos
    nacos:
      application: seata-server
      server-addr: 127.0.0.1:8848
      namespace: ""
      group: SEATA_GROUP
  config:
    type: nacos
    nacos:
      server-addr: 127.0.0.1:8848
      namespace: ""
      group: SEATA_GROUP
      data-id: seataServer.properties
```

## 使用方式

### 在业务方法上添加 @GlobalTransactional 注解

在需要分布式事务的业务方法上添加 `@GlobalTransactional` 注解：

```java
import io.seata.spring.annotation.GlobalTransactional;

@Service
public class OrderServiceImpl implements OrderService {
    
    @Override
    @GlobalTransactional(name = "order-create-tx", rollbackFor = Exception.class)
    public Long createOrder(OrderDTO orderDTO) {
        // 1. 验证用户（远程调用 user-service）
        // 2. 创建订单
        // 3. 如果发生异常，整个事务会回滚
    }
}
```

### 主要参数说明

- `name`: 全局事务的名称，用于标识和追踪
- `rollbackFor`: 指定哪些异常触发回滚，建议设置为 `Exception.class`
- `noRollbackFor`: 指定哪些异常不触发回滚
- `timeout`: 事务超时时间（毫秒），默认 60000

## 已集成的分布式事务场景

### 1. 订单创建事务

方法：`OrderServiceImpl.createOrder()`

该方法使用 `@GlobalTransactional` 注解，确保以下操作的原子性：
- 验证用户存在（调用 user-service）
- 创建订单记录

如果任一步骤失败，整个事务将回滚。

### 2. 订单支付事务

方法：`OrderServiceImpl.payOrder()`

该方法使用 `@GlobalTransactional` 注解，确保订单状态更新的一致性。

### 3. 订单取消事务

方法：`OrderServiceImpl.cancelOrder()`

该方法使用 `@GlobalTransactional` 注解，确保订单取消操作的一致性。

## 测试分布式事务

### 1. 正常提交场景

```bash
# 创建订单（正常情况）
curl -X POST http://localhost:8082/api/order \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "productName": "测试商品",
    "productPrice": 99.99,
    "quantity": 2
  }'
```

### 2. 回滚场景

为了测试回滚场景，可以：

1. 修改代码在事务中抛出异常
2. 使用不存在的用户ID创建订单
3. 断开其中一个服务的数据库连接

观察日志中的 Seata 事务信息：

```
开始创建订单，启用分布式事务
Global transaction [xid] has been created
Branch transaction [branchId] has been registered
```

如果发生异常，会看到回滚日志：

```
Global transaction [xid] has been rollback
Branch transaction [branchId] has been rollback
```

## 监控和运维

### 查看 Seata 控制台

访问 Seata 控制台：http://localhost:7091

默认用户名/密码：seata/seata

### 查看事务日志

1. 查看全局事务：
```sql
SELECT * FROM seata.global_table ORDER BY gmt_create DESC LIMIT 10;
```

2. 查看分支事务：
```sql
SELECT * FROM seata.branch_table ORDER BY gmt_create DESC LIMIT 10;
```

3. 查看锁信息：
```sql
SELECT * FROM seata.lock_table ORDER BY gmt_create DESC LIMIT 10;
```

4. 查看 undo_log：
```sql
SELECT * FROM luckyh_cloud.undo_log ORDER BY log_created DESC LIMIT 10;
```

## 性能优化建议

1. **合理设置超时时间**：根据业务场景调整 `timeout` 参数
2. **最小化事务范围**：只在必要的方法上使用 `@GlobalTransactional`
3. **避免长事务**：分布式事务会锁定资源，应尽快完成
4. **使用异步消息**：对于一些最终一致性场景，可以考虑使用 MQ 替代 Seata
5. **监控事务性能**：定期检查事务耗时和成功率

## 常见问题

### 1. 服务启动报错：找不到 seata-server

**解决方案**：确保 Seata Server 已启动，并在 Nacos 中正确注册。

### 2. 事务不生效

**可能原因**：
- `@GlobalTransactional` 注解未生效（需要在 Spring 管理的 Bean 上使用）
- 数据源未正确配置代理
- Seata Server 连接失败

### 3. 数据库连接超时

**解决方案**：增加 `store.db.maxWait` 配置值。

### 4. undo_log 表不存在

**解决方案**：在业务数据库中执行 `sql/seata-undo-log.sql` 脚本。

## 注意事项

1. **数据源代理**：Seata 会自动代理数据源，不需要手动配置
2. **事务隔离级别**：默认使用读已提交（READ_COMMITTED）
3. **跨服务调用**：确保所有参与分布式事务的服务都已集成 Seata
4. **异常处理**：业务代码应该抛出异常以触发回滚，而不是返回错误码
5. **幂等性**：分布式事务可能会重试，业务代码应该保证幂等性

## 参考资料

- [Seata 官方文档](https://seata.io/zh-cn/docs/overview/what-is-seata.html)
- [Spring Cloud Alibaba Seata](https://github.com/alibaba/spring-cloud-alibaba/wiki/Seata)
- [Seata AT 模式](https://seata.io/zh-cn/docs/dev/mode/at-mode.html)
