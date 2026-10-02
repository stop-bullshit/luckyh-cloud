# LuckyH Cloud

Java 17 + Spring Boot 3 + Spring Cloud 的微服务 demo，包含网关、认证、业务用户、库存、账户和订单六个应用。

## 资料入口

| 资料 | 内容 |
| --- | --- |
| [本地开发与启动](support/docs/development.md) | 首次安装顺序、Windows 启停、IDEA、接口验证、前端联调 |
| [模块与业务说明](support/docs/architecture.md) | 公共模块边界、服务职责、表归属、接口和事务范围 |
| [Nacos 配置](support/nacos/README.md) | Data ID、命名空间、环境变量、配置发布 |
| [数据库初始化](support/sql/README.md) | 初始化与升级脚本、执行顺序、演示账号 |
| [商品管理](support/docs/product-management.md) | 商品新增、编辑、补货，以及购买页的商品选择 |
| [余额管理](support/docs/account-management.md) | 业务用户余额查询和增量充值 |
| [跨服务事务测试](support/docs/distributed-transactions.md) | 三库提交、全写后回滚、库存 / 余额不足、服务不可用与 SQL 核对 |
| [K8s Seata 部署](support/k8s/README.md) | 协调器、Secret、固定地址和 Ingress |
| [常见问题](support/docs/troubleshooting.md) | Nacos 加载失败、数据库、服务发现、Seata 和端口冲突 |

## 项目结构

```text
luckyh-cloud/
├── luckyh-common/
│   ├── luckyh-common-core/  # 响应对象、常量、基础工具
│   ├── luckyh-common-web/   # MVC 用户上下文与拦截器
│   ├── luckyh-common-redis/ # Redis 操作与令牌黑名单
│   └── luckyh-common-mq/    # 独立 RabbitMQ 示例
├── luckyh-gateway-service/ # 8080：路由、跨域、令牌校验
├── luckyh-auth-service/    # 8083：登录、注册、JWT、认证表
├── luckyh-user-service/    # 8081：业务用户资料
├── luckyh-inventory-service/ # 8084：商品管理、补货、库存扣减
├── luckyh-account-service/ # 8085：账户查询、充值、余额扣减
├── luckyh-order-service/   # 8082：订单、购买编排、Feign、Seata AT
├── support/              # 文档、配置、部署和运行资料
│   ├── docs/             # 开发、模块、排障文档
│   ├── sql/              # 三个业务库与 Seata 库初始化
│   ├── nacos/            # 应发布到 Nacos 的配置
│   ├── k8s/              # Seata 部署清单
│   └── scripts/          # PowerShell 启停脚本
├── pom.xml               # 根 Maven 聚合配置
├── start-services.bat    # Windows 启动入口
└── stop-services.bat     # Windows 停止入口
```

`luckyh-common/` 和六个服务保持根目录模块层级；配套资料集中在 `support/`，根目录保留项目说明和 Windows 启停入口。公共与业务模块的目录和 Maven 名称均使用 `luckyh-` 前缀，运行应用名、Nacos 服务名与 Data ID 保持原值，具体对应关系见 [模块说明](support/docs/architecture.md#服务与公共模块)。

## 当前环境连接目标

| 组件 | 地址 | 说明 |
| --- | --- | --- |
| Nacos 客户端 | `192.168.10.201:8848` | 同时需要可达 gRPC `9848` |
| Nacos 控制台 | `http://nacos.home/` | Ingress，业务命名空间 ID 为 `luckyh-cloud` |
| MySQL | `192.168.10.209:3306` | 业务库 `luckyh_cloud`、`luckyh_inventory`、`luckyh_account`；协调器库 `seata` |
| Redis | `192.168.10.13:6379` | 登录令牌黑名单 |
| Seata 协调器 | `192.168.10.203:8091` | 固定地址，配置由 Nacos 下发 |
| Seata 控制台 | `http://seata.home/` | Ingress，后端端口 `7091` |

地址的文件来源与覆盖方式见 [Nacos 配置](support/nacos/README.md)。密码按配置说明提供。

## 启动

需要 JDK 17、Maven 3.9，以及已初始化的 Nacos、MySQL、Redis、Seata。首次环境准备按 [开发文档](support/docs/development.md) 执行。

```powershell
cd D:\project\demo\luckyh-cloud
.\start-services.bat
```

脚本构建 JAR，依次启动认证、用户、库存、账户、订单、网关，并等待健康检查 `UP`。停止使用 `.\stop-services.bat`；无需重新构建时使用 `.\start-services.bat -SkipBuild`。

网关：<http://localhost:8080>。演示账号 `admin`、`user001`、`user002`，密码均为 `123456`。

配套原版 Ant Design Pro 前端位于 `D:\project\demo\luckyh-cloud-web`，开发入口 <http://localhost:8000>。

## 本次验证进度（2026-10-02）

17 个测试已实际执行通过（16 个新增、1 个已有 common-core）；6 份测试报告合计 errors / failures / skipped 均为 0。根项目 12 个 Maven 模块已完成打包，最后一次仅配置变更的打包使用 `skipTests`，测试此前单独实跑。

客户端传输参数由 Nacos public / `SEATA_GROUP` / `seataServer.properties` 提供：`transport.heartbeat=false`、`transport.rpcTmRequestTimeout=30000`、`transport.rpcRmRequestTimeout=15000`，不带 `seata.` 前缀，后两项单位毫秒。本地 Spring YAML 心跳项已删除；TC 使用 K8s 清单中的 `seata.transport.heartbeat=false`。这些静态参数需要重启并核对 JVM，Nacos 刷新不足以确认生效。

2026-10-02 14:38–14:40，正常提交、全写后回滚、库存不足、余额不足、账户不可用五个真实场景均已通过三库数据核对。TC Ready，TC 与四个客户端的实际 JVM 心跳 / 读空闲 / 写空闲均为 `false / 0 / 0`，客户端 TM / RM RPC 为 `30000ms / 15000ms`。约 14:41 账户恢复后，六应用 `8080–8085` 健康均为 `UP`，Nacos 六个服务健康注册均通过。

关闭心跳仍是当前 demo 的长回滚兼容处理，网络 / 存储延迟修复后需两端同步恢复心跳。订单网关路由等待为 60 秒，RPC 重试总时长不保证在 60 秒内。详细实测记录及边界见 [事务指南](support/docs/distributed-transactions.md#8-本次实测记录)。
