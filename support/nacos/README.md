# Nacos 配置

此目录保存应用需要发布到 Nacos 的配置内容。文件名就是 Data ID；修改本地文件不会自动更新 Nacos。项目启动需要配置已存在，`spring.config.import` 使用必需导入。

源码目录与 Maven 名称加上了 `luckyh-` 前缀；运行应用名、Nacos 注册名和配置文件名沿用原值。例如 `luckyh-auth-service` 仍使用应用名 `auth-service` 和 Data ID `auth-service.yml`，完整对照见 [模块说明](../docs/architecture.md#服务与公共模块)。

## 配置清单

| 命名空间 ID | Group | Data ID | 内容 / 使用方 |
| --- | --- | --- | --- |
| `luckyh-cloud` | `DEFAULT_GROUP` | [db-common.yml](db-common.yml) | MySQL 公共连接参数、Redis、RabbitMQ、MyBatis Plus；六个服务均导入 |
| `luckyh-cloud` | `DEFAULT_GROUP` | [common.yml](common.yml) | Spring Boot 默认日志显示、监控端点；六个服务均导入 |
| `luckyh-cloud` | `DEFAULT_GROUP` | [gateway-service.yml](gateway-service.yml) | 网关路由、CORS、HTTP 超时 |
| `luckyh-cloud` | `DEFAULT_GROUP` | [auth-service.yml](auth-service.yml) | JWT |
| `luckyh-cloud` | `DEFAULT_GROUP` | [user-service.yml](user-service.yml) | 用户服务配置 |
| `luckyh-cloud` | `DEFAULT_GROUP` | [inventory-service.yml](inventory-service.yml) | 仅声明库存库名 `luckyh_inventory` |
| `luckyh-cloud` | `DEFAULT_GROUP` | [account-service.yml](account-service.yml) | 仅声明账户库名 `luckyh_account` |
| `luckyh-cloud` | `DEFAULT_GROUP` | [order-service.yml](order-service.yml) | 订单服务配置 |
| public（空 ID） | `SEATA_GROUP` | [seataServer.properties](seataServer.properties) | 用户、库存、账户、订单的事务组、协调器地址、通信心跳及 TM / RM RPC 超时 |

清单共九个 Data ID：八份业务 YAML 加一份 Seata properties。Seata 配置属于单独的 public 命名空间和 `SEATA_GROUP`，不能与八份业务配置一起导入 `luckyh-cloud`。

## 连接参数放在哪里

六个服务的本地 `src/main/resources/application.yml` 负责服务名、启动端口、Nacos 连接及导入配置；用户、库存、账户、订单服务还包含 Seata 客户端配置。订单本地配置包含 Feign 超时和熔断线程设置。Nacos 内的配置负责数据源、网关、JWT 和共享运行参数，避免远端配置覆盖读取自身所需的 Nacos 地址。

| 项目 | 当前配置 |
| --- | --- |
| Nacos 控制台 | [http://nacos.home/](http://nacos.home/) |
| Nacos 客户端 | `192.168.10.201:8848`；客户端还需要可达 `192.168.10.201:9848` |
| MySQL | `192.168.10.209:3306`；`luckyh_cloud`、`luckyh_inventory`、`luckyh_account` |
| Redis API | `redis-api.home:6379`（`hosts` / DNS → `192.168.10.202`，Redis Cluster 代理单端口） |
| Redis Cluster 直连（可选） | `192.168.10.205:6379–6384`（六个节点端口） |
| RabbitMQ AMQP | `rabbit-api.home:5672`（`hosts` / DNS → `192.168.10.204`） |
| Seata 协调器 | `192.168.10.203:8091` |

`nacos.home` 是控制台的 Ingress 域名，应用的 Nacos 客户端应使用上表的客户端入口。

`db-common.yml` 的 `spring.data.redis.host/port` 默认通过 Envoy 代理 `redis-api.home:6379` 连接 Redis Cluster，普通客户端使用 RESP2，不需要 Redis 节点的 Windows `hosts` 映射。同一份 Nacos 配置还保存逗号分隔的六个直连入口 `luckyh.redis.cluster-nodes`。需要使用 Cluster 客户端时，在对应进程的 PowerShell 中设置：

```powershell
$env:SPRING_DATA_REDIS_CLUSTER_NODES = '${luckyh.redis.cluster-nodes}'
```

启动或重启应用后，Spring 从 Nacos 读取节点列表并使用 Cluster 模式；其他进程仍走代理。切回代理时在该进程执行 `Remove-Item Env:SPRING_DATA_REDIS_CLUSTER_NODES`，然后重启应用。Cluster 客户端发现槽位后必须能解析和访问全部六个节点；所需域名、端口及 `hosts` 内容见 [Redis Cluster 直连说明](../k8s/middleware.md#本机访问)。密码仍使用 `REDIS_PASSWORD`。`db-common.yml` 同时提供 `spring.rabbitmq.host/port/username/password`，RabbitMQ 用户名为 `admin`，密码由 `RABBITMQ_PASSWORD` 提供；六个业务服务目前尚未接入 MQ 模块。`redis.home` 与 `rabbit.home` 只用于浏览器管理页面，Ingress 地址为 `192.168.10.200:80`；应用分别连接 `redis-api.home:6379` 和 `rabbit-api.home:5672`。运行应用的每台机器都须配置上述数据域名的 DNS / `hosts`，或覆盖对应的 Spring 主机配置。

2026-10-02 已将 Redis 代理域名、Cluster 节点列表及 RabbitMQ AMQP 配置发布到 Nacos `luckyh-cloud / DEFAULT_GROUP / db-common.yml`，远端回读与本地文件一致。本地认证服务加载了新增属性，默认代理模式的 Redis 与整体健康检查均为 `UP`；此前直连 Cluster 模式也已验证为 `UP`。RabbitMQ 管理 API 认证和本机 AMQP 端口连通均通过。其他已运行的服务应重启后核对实际连接。

## 环境变量

| 变量 | 用途 / 默认值 |
| --- | --- |
| `NACOS_SERVER_ADDR` | Nacos 客户端地址，默认 `192.168.10.201:8848` |
| `NACOS_USERNAME` | Nacos 登录用户名，默认 `nacos` |
| `NACOS_PASSWORD` | Nacos 登录密码，必须提供，无默认值 |
| `NACOS_NAMESPACE` | 业务配置和服务注册的命名空间 ID，默认 `luckyh-cloud` |
| `NACOS_GROUP` | 业务配置和服务注册分组，默认 `DEFAULT_GROUP` |
| `SEATA_NACOS_SERVER_ADDR` | Seata 配置所在 Nacos；默认跟随 `NACOS_SERVER_ADDR`，仍使用 public / `SEATA_GROUP` |
| `DB_HOST` | 覆盖 `db-common.yml` 的 MySQL 主机 |
| `DB_PORT` | 覆盖 `db-common.yml` 的 MySQL 端口 |
| `DB_USERNAME` | 覆盖 `db-common.yml` 的应用数据库账号 |
| `DB_PASSWORD` | 覆盖 `db-common.yml` 的应用数据库密码；未设置时沿用配置内现有演示环境值 |
| `REDIS_PASSWORD` | Redis Cluster 密码，`db-common.yml` 必需；一键启动脚本未发现该变量时会通过 `ssh k8s-master` 读取 `redis/redis-auth` Secret |
| `RABBITMQ_PASSWORD` | RabbitMQ 客户端连接时所需密码；一键启动脚本未发现该变量时会通过 `ssh k8s-master` 读取 `rabbitmq/rabbitmq-auth` Secret |
| `SPRING_DATA_REDIS_CLUSTER_NODES` | 可选；设为 `'${luckyh.redis.cluster-nodes}'` 时从 Nacos 读取直连节点列表，未设置则使用代理 |
| `JWT_SECRET` | 覆盖认证服务 JWT 密钥；配置保留演示默认值，自定义 HS512 密钥至少 64 字节 |
| `SPRING_CLOUD_NACOS_DISCOVERY_IP` | 指定服务注册的实际 IPv4 地址 |
| `SPRING_CLOUD_NACOS_DISCOVERY_NETWORK_INTERFACE` | IDE 启动时可指定 Java 网卡名；按本机实际网卡设置 |

数据库主机、端口、账号、密码和 JDBC 参数只在 `db-common.yml` 维护。公共 URL 使用 `${luckyh.datasource.database:luckyh_cloud}` 选择数据库：认证、用户、订单默认使用 `luckyh_cloud`，库存和账户的服务配置只分别声明 `luckyh_inventory`、`luckyh_account`。切换 MySQL 环境时只发布 `db-common.yml`；只有数据库名称变化时才修改对应服务配置。Seata 服务端使用独立数据库和 Kubernetes Secret，见 [K8s 部署说明](../k8s/README.md)。

一键启动脚本读取当前进程或 Windows 用户环境变量中的 Nacos 凭据及 Redis、RabbitMQ 密码；未设置 `REDIS_PASSWORD` / `RABBITMQ_PASSWORD` 时会通过 `ssh k8s-master` 从对应 Kubernetes Secret 读取到当前进程环境，再传给启动的 Java 进程。直接从 IDEA 启动时，应预先设置所用中间件的密码变量。IDEA 继承的是打开 IDE 时的环境，设置 Windows 用户环境变量后需要完全退出并重新打开 IDEA。不要把凭据写入共享 IDE 运行配置。

完整启动步骤见 [本地开发](../docs/development.md)。Windows / JDK 17 启动时，VM options 需要 `-Dfile.encoding=UTF-8`；它应放在 Java 类名或 `-jar` 之前。否则中文 YAML 解析失败可能被包装为“配置不存在”。

## 新环境准备

1. 按 [数据库初始化说明](../sql/README.md) 初始化业务库；准备 Redis 和 Nacos 登录账号。
2. 在 Nacos 控制台创建命名空间，**ID** 为 `luckyh-cloud`。显示名称可以自定，应用使用 ID 连接。
3. 在这个命名空间、`DEFAULT_GROUP` 下创建清单中的八个 YAML 配置，内容分别取自对应文件；新环境的 MySQL 地址和凭据统一修改 `db-common.yml`。
4. 部署 Seata 后，在 public（空 ID）、`SEATA_GROUP` 下创建 `seataServer.properties`，核对事务组 `default_tx_group` 和协调器地址。
5. 设置应用环境变量，再启动六个服务。检查配置读取、Nacos 服务注册及 `/actuator/health`。

当前环境已有数据库和配置时，保留现有数据与凭据；整理仓库文件不代表重新初始化或自动发布。

## 为已有环境补齐购买演示

1. 按 [03-distributed-demo.sql](../sql/03-distributed-demo.sql) 补齐库存与账户两个业务库，检查应用账号读写权限。
2. 在 `luckyh-cloud` / `DEFAULT_GROUP` 新增 `inventory-service.yml` 和 `account-service.yml`。
3. 更新 `gateway-service.yml` 的两个新路由：`/api/inventory/**` → `lb://inventory-service`、`/api/account/**` → `lb://account-service`，均为 `StripPrefix=2`。
4. 核对 public / `SEATA_GROUP` 中现有协调器映射，启动或重启受影响应用，检查六个服务注册和健康状态。

以上为操作步骤，仓库修改不会自动更新运行环境。三库事务的请求和数据核对见 [跨服务事务测试](../docs/distributed-transactions.md)。

## 当前 demo 的事务超时配置

`gateway-service.yml` 的订单路由 metadata 使用 `response-timeout: 60000`（毫秒），适配当前环境的长回滚；其他路由仍使用全局 `spring.cloud.gateway.httpclient.response-timeout: 30s`。发布后核对网关实际加载的路由配置，不能把 60 秒视为整个事务或 RPC 重试的完成上限。

四个客户端使用 Seata Nacos provider，传输参数的配置来源是 **public / `SEATA_GROUP` / `seataServer.properties`**。真实 JVM 核对确认本地 Spring YAML 心跳项未用于这一 provider 的传输参数读取，仓库已删除该本地项。客户端配置必须使用下列精确字段，不带 `seata.` 前缀，后两项单位为毫秒：

```properties
transport.heartbeat=false
transport.rpcTmRequestTimeout=30000
transport.rpcRmRequestTimeout=15000
```

TC 使用 file provider，心跳仍在 `support/k8s/seata.yml` 中配置为 `seata.transport.heartbeat=false`。客户端传输参数含 JVM 静态初始化值，Nacos 发布 / 刷新后仍须重启用户、库存、账户、订单，核对 JVM 实际心跳 `false`、读 / 写空闲 `0 / 0` 和 RPC 超时；不能只改单端。修复网络 / 存储延迟后，在上述 public 配置与 TC YAML 同步恢复心跳为 `true`，再重启 TC 和四个客户端。详细原因与复验要求见 [事务指南](../docs/distributed-transactions.md#当前-demo-的长回滚兼容处理)。

2026-10-02 已核对 TC 与四个客户端实际 JVM 心跳 / 读空闲 / 写空闲为 `false / 0 / 0`、客户端 RPC 为 `30000ms / 15000ms`。14:38–14:40 五个事务场景均通过三库核对；约 14:41 账户恢复后六应用健康均 `UP`，Nacos 六服务健康注册均通过。实测数据见 [事务记录](../docs/distributed-transactions.md#8-本次实测记录)。

## 覆盖顺序与刷新

### 日志输出

业务服务使用 Spring Boot 默认 Logback 控制台格式，`common.yml` 强制启用默认 ANSI 颜色。各业务 Mapper 的 SLF4J Logger 固定为 DEBUG，保留 MyBatis 的 SQL、参数和结果数量；不要开启 TRACE，避免打印查询字段和逐行结果。`db-common.yml` 不再指定 MyBatis `StdOutImpl`，防止 SQL 日志绕过日志框架直接写到标准输出。

日志格式、MyBatis 日志实现及已有 Logger 级别可能已在应用启动时初始化。发布 `common.yml`、`db-common.yml` 和各服务配置后需重启六个服务，不能只依赖动态刷新。

启动命令参数、JVM 系统属性和环境变量可以覆盖配置文件值。当前业务配置的导入顺序是 `db-common.yml`、`common.yml`、`${spring.application.name}.yml`。库存和账户的最后一份配置只提供 `luckyh.datasource.database`，公共 URL 在环境合并后引用该值，不再覆盖完整数据源 URL。

导入项启用了 `refresh=true`。运行中的 Bean 是否立即变化取决于绑定方式；端口、连接地址、JWT 密钥等变更后应重启服务。已有令牌依赖签发时的 JWT 密钥，切换密钥后需要重新登录。

本目录不提供自动发布脚本。需要更新远端时，在正确的命名空间、Group、Data ID 下核对并发布对应文件。排错见 [故障排查](../docs/troubleshooting.md)。
