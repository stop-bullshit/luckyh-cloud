# LuckyH Cloud

Java 17 + Spring Boot 3 + Spring Cloud 的微服务 demo，包含网关、认证、业务用户和订单服务。

## 资料入口

| 资料 | 内容 |
| --- | --- |
| [本地开发与启动](support/docs/development.md) | 首次安装顺序、Windows 启停、IDEA、接口验证、前端联调 |
| [模块与业务说明](support/docs/architecture.md) | 公共模块边界、服务职责、表归属、接口和事务范围 |
| [Nacos 配置](support/nacos/README.md) | Data ID、命名空间、环境变量、配置发布 |
| [数据库初始化](support/sql/README.md) | 两份初始化脚本、执行顺序、演示账号 |
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
├── luckyh-order-service/   # 8082：订单、Feign、Seata AT
├── support/              # 文档、配置、部署和运行资料
│   ├── docs/             # 开发、模块、排障文档
│   ├── sql/              # 业务库与 Seata 库初始化
│   ├── nacos/            # 应发布到 Nacos 的配置
│   ├── k8s/              # Seata 部署清单
│   └── scripts/          # PowerShell 启停脚本
├── pom.xml               # 根 Maven 聚合配置
├── start-services.bat    # Windows 启动入口
└── stop-services.bat     # Windows 停止入口
```

`luckyh-common/` 和四个业务服务保持根目录模块层级；配套资料集中在 `support/`，根目录保留项目说明和 Windows 启停入口。公共与业务模块的目录和 Maven 名称均使用 `luckyh-` 前缀，运行应用名、Nacos 服务名与 Data ID 保持原值，具体对应关系见 [模块说明](support/docs/architecture.md#服务与公共模块)。

## 当前环境连接目标

| 组件 | 地址 | 说明 |
| --- | --- | --- |
| Nacos 客户端 | `192.168.10.201:8848` | 同时需要可达 gRPC `9848` |
| Nacos 控制台 | `http://nacos.home/` | Ingress，业务命名空间 ID 为 `luckyh-cloud` |
| MySQL | `192.168.10.13:3380` | 业务库 `luckyh_cloud`，事务库 `seata` |
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

脚本构建 JAR，依次启动认证、用户、订单、网关，并等待健康检查 `UP`。停止使用 `.\stop-services.bat`；无需重新构建时使用 `.\start-services.bat -SkipBuild`。

网关：<http://localhost:8080>。演示账号 `admin`、`user001`、`user002`，密码均为 `123456`。

配套原版 Ant Design Pro 前端位于 `D:\project\demo\luckyh-cloud-web`，开发入口 <http://localhost:8000>。
