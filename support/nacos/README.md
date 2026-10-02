# Nacos 配置

此目录保存应用需要发布到 Nacos 的配置内容。文件名就是 Data ID；修改本地文件不会自动更新 Nacos。项目启动需要配置已存在，`spring.config.import` 使用必需导入。

源码目录与 Maven 名称加上了 `luckyh-` 前缀；运行应用名、Nacos 注册名和配置文件名沿用原值。例如 `luckyh-auth-service` 仍使用应用名 `auth-service` 和 Data ID `auth-service.yml`，完整对照见 [模块说明](../docs/architecture.md#服务与公共模块)。

## 配置清单

| 命名空间 ID | Group | Data ID | 内容 / 使用方 |
| --- | --- | --- | --- |
| `luckyh-cloud` | `DEFAULT_GROUP` | [db-common.yml](db-common.yml) | MySQL、Redis、MyBatis Plus；四个服务均导入 |
| `luckyh-cloud` | `DEFAULT_GROUP` | [common.yml](common.yml) | 共享日志格式、监控端点；四个服务均导入 |
| `luckyh-cloud` | `DEFAULT_GROUP` | [gateway-service.yml](gateway-service.yml) | 网关路由、CORS、HTTP 超时 |
| `luckyh-cloud` | `DEFAULT_GROUP` | [auth-service.yml](auth-service.yml) | JWT、认证服务日志 |
| `luckyh-cloud` | `DEFAULT_GROUP` | [user-service.yml](user-service.yml) | 用户服务日志 |
| `luckyh-cloud` | `DEFAULT_GROUP` | [order-service.yml](order-service.yml) | 订单服务日志 |
| public（空 ID） | `SEATA_GROUP` | [seataServer.properties](seataServer.properties) | 用户、订单服务的 Seata 事务组与协调器地址 |

用户、订单服务的 Seata 配置属于单独的 public 命名空间和 `SEATA_GROUP`，不能与上面的六份业务配置一起导入 `luckyh-cloud`。

## 连接参数放在哪里

四个服务的本地 `src/main/resources/application.yml` 负责服务名、启动端口、Nacos 连接及导入配置；用户、订单服务还包含 Seata 客户端配置。Nacos 内的配置负责数据源、网关、JWT 和共享运行参数，避免远端配置覆盖读取自身所需的 Nacos 地址。

| 项目 | 当前配置 |
| --- | --- |
| Nacos 控制台 | [http://nacos.home/](http://nacos.home/) |
| Nacos 客户端 | `192.168.10.201:8848`；客户端还需要可达 `192.168.10.201:9848` |
| MySQL | `192.168.10.13:3380/luckyh_cloud` |
| Redis | `192.168.10.13:6379` |
| Seata 协调器 | `192.168.10.203:8091` |

`nacos.home` 是控制台的 Ingress 域名，应用的 Nacos 客户端应使用上表的客户端入口。

## 环境变量

| 变量 | 用途 / 默认值 |
| --- | --- |
| `NACOS_SERVER_ADDR` | Nacos 客户端地址，默认 `192.168.10.201:8848` |
| `NACOS_USERNAME` | Nacos 登录用户名，默认 `nacos` |
| `NACOS_PASSWORD` | Nacos 登录密码，必须提供，无默认值 |
| `NACOS_NAMESPACE` | 业务配置和服务注册的命名空间 ID，默认 `luckyh-cloud` |
| `NACOS_GROUP` | 业务配置和服务注册分组，默认 `DEFAULT_GROUP` |
| `SEATA_NACOS_SERVER_ADDR` | Seata 配置所在 Nacos；默认跟随 `NACOS_SERVER_ADDR`，仍使用 public / `SEATA_GROUP` |
| `DB_PASSWORD` | 覆盖 `db-common.yml` 的应用数据库密码；未设置时沿用配置内现有演示环境值 |
| `JWT_SECRET` | 覆盖认证服务 JWT 密钥；配置保留演示默认值，自定义 HS512 密钥至少 64 字节 |
| `SPRING_CLOUD_NACOS_DISCOVERY_IP` | 指定服务注册的实际 IPv4 地址 |
| `SPRING_CLOUD_NACOS_DISCOVERY_NETWORK_INTERFACE` | IDE 启动时可指定 Java 网卡名；按本机实际网卡设置 |

数据库地址、账号以及 Redis 地址目前直接放在 `db-common.yml`。Seata 服务端使用独立数据库和 Kubernetes Secret，见 [K8s 部署说明](../k8s/README.md)。

一键启动脚本读取当前进程或 Windows 用户环境变量中的 Nacos 凭据；其他覆盖变量需要存在于启动进程环境中。IDEA 继承的是打开 IDE 时的环境，设置 Windows 用户环境变量后需要完全退出并重新打开 IDEA。不要把凭据写入共享 IDE 运行配置。

完整启动步骤见 [本地开发](../docs/development.md)。Windows / JDK 17 启动时，VM options 需要 `-Dfile.encoding=UTF-8`；它应放在 Java 类名或 `-jar` 之前。否则中文 YAML 解析失败可能被包装为“配置不存在”。

## 新环境准备

1. 按 [数据库初始化说明](../sql/README.md) 初始化业务库；准备 Redis 和 Nacos 登录账号。
2. 在 Nacos 控制台创建命名空间，**ID** 为 `luckyh-cloud`。显示名称可以自定，应用使用 ID 连接。
3. 在这个命名空间、`DEFAULT_GROUP` 下创建清单中的六个 YAML 配置，内容分别取自对应文件；调整新环境的数据源地址和凭据。
4. 部署 Seata 后，在 public（空 ID）、`SEATA_GROUP` 下创建 `seataServer.properties`，核对事务组 `default_tx_group` 和协调器地址。
5. 设置应用环境变量，再启动四个服务。检查配置读取、Nacos 服务注册及 `/actuator/health`。

当前环境已有数据库和配置时，保留现有数据与凭据；整理仓库文件不代表重新初始化或自动发布。

## 覆盖顺序与刷新

启动命令参数、JVM 系统属性和环境变量可以覆盖配置文件值。当前业务配置的导入顺序是 `db-common.yml`、`common.yml`、`${spring.application.name}.yml`；同名属性以后导入的配置为准，导入的配置也可覆盖本地 `application.yml` 中同名值。

导入项启用了 `refresh=true`。运行中的 Bean 是否立即变化取决于绑定方式；端口、连接地址、JWT 密钥等变更后应重启服务。已有令牌依赖签发时的 JWT 密钥，切换密钥后需要重新登录。

本目录不提供自动发布脚本。需要更新远端时，在正确的命名空间、Group、Data ID 下核对并发布对应文件。排错见 [故障排查](../docs/troubleshooting.md)。
