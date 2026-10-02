# 模块与业务说明

根 `pom.xml` 聚合 `luckyh-common/` 和四个 `luckyh-*-service/` 业务模块，模块保持根目录层级。`support/` 存放开发文档、初始化 SQL、Nacos 配置、K8s 清单和启停脚本，不参与 Maven 模块构建。

## 服务与公共模块

| 模块（目录 / Maven 名称） | 运行应用名 | 职责 | 直接使用的公共模块 |
| --- | --- | --- | --- |
| luckyh-gateway-service | gateway-service | WebFlux 网关、路由、跨域、调用认证服务校验令牌 | 无 |
| luckyh-auth-service | auth-service | 账号、注册、登录、JWT、角色权限查询、Redis 黑名单 | luckyh-common-core、luckyh-common-redis |
| luckyh-user-service | user-service | 业务用户资料 CRUD 与分页 | luckyh-common-core、luckyh-common-web |
| luckyh-order-service | order-service | 订单创建、查询、支付、取消，Feign 查询业务用户 | luckyh-common-core、luckyh-common-web |
| luckyh-common | — | 聚合四个公共子模块的 POM | luckyh-common-core、luckyh-common-web、luckyh-common-redis、luckyh-common-mq |
| luckyh-common-core | — | `Result`、`R`、基础常量和工具 | 无业务模块依赖 |
| luckyh-common-web | — | Servlet 用户上下文、读取请求头的拦截器 | 面向 MVC 服务 |
| luckyh-common-redis | — | Redis 操作、令牌黑名单 | 面向 Redis 使用方 |
| luckyh-common-mq | — | RabbitMQ 配置、消息类型、生产者与消费者示例 | 独立示例 |

目录名、Maven `artifactId` 和项目 `name` 使用左列名称；`spring.application.name` 与 Nacos 注册名使用运行应用名，服务配置 Data ID 仍为 `auth-service.yml`、`user-service.yml`、`order-service.yml`、`gateway-service.yml`。Java 包名继续使用 `com.luckyh.cloud.*`。

DTO、VO、Entity、Mapper、Service 属于各自业务服务。订单 Feign 客户端及其配置属于 `luckyh-order-service`。网关使用 WebFlux，不能引入 Servlet 的 `luckyh-common-web`。

用户、订单普通响应为 `Result`；登录保留 `R`，额外序列化 `success`、`fail`。二者都有 `code`、`message`、`data`，成功业务码为 `200`。接口可能以 HTTP 200 返回业务失败，调用方需检查 `code`；网关拒绝认证时返回 HTTP 401。

## 数据归属

| 库 | 表 | 使用方 |
| --- | --- | --- |
| luckyh_cloud | `sys_user`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission` | luckyh-auth-service |
| luckyh_cloud | `user` | luckyh-user-service |
| luckyh_cloud | `order_info` | luckyh-order-service |
| luckyh_cloud | `undo_log` | Seata AT 客户端 |
| seata | `global_table`、`branch_table`、`lock_table`、`distributed_lock` | Seata 协调器 |

登录账号 `sys_user` 与订单关联的业务用户 `user` 是两套数据。注册登录账号不会自动创建业务用户，用户资料维护也不会创建登录账号。当前 demo 的三个数据库服务共用一个业务库。

## 调用流程

登录由网关转发到认证服务，认证服务读取账号及角色权限，验证 BCrypt 密码并签发 JWT。业务请求带 `Authorization: Bearer <accessToken>`；网关通过服务发现调用认证服务校验令牌及 Redis 黑名单，再转发用户或订单请求。退出将令牌加入 Redis 黑名单。

`luckyh-common-web` 从 `X-User-*` 请求头读取上下文并在请求结束后清理；当前网关的令牌校验不自动填充这些头，不能把上下文组件视为已完成的权限或归属校验。

订单创建先通过 Feign 查询业务用户，再计算金额、生成订单编号并保存 `order_info`。订单详情和分页会查询用户资料。支付与取消只接受待支付状态：`0` 待支付、`1` 已支付、`2` 已取消。演示支付更新订单状态，没有接入支付平台。

## 网关接口

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/api/auth/login` | 账号密码登录 |
| POST | `/api/auth/register` | 注册账号，包含 username、password、confirmPassword、realName、userType |
| POST | `/api/auth/refresh` | 刷新令牌，参数 refreshToken |
| GET | `/api/auth/validate` | 校验访问令牌 |
| POST | `/api/auth/logout` | 注销访问令牌 |
| GET | `/api/user/users` | 用户分页，current、size、username |
| GET | `/api/user/users/{id}` | 用户详情 |
| POST | `/api/user/users` | 新建业务用户 |
| PUT | `/api/user/users/{id}` | 更新业务用户 |
| DELETE | `/api/user/users/{id}` | 删除业务用户 |
| GET | `/api/order/orders` | 订单分页，current、size、userId |
| GET | `/api/order/orders/{id}` | 订单详情 |
| POST | `/api/order/orders` | 创建订单，userId、productName、productPrice、quantity |
| POST | `/api/order/orders/{id}/pay` | 演示支付 |
| POST | `/api/order/orders/{id}/cancel` | 取消待支付订单 |

认证路由 `StripPrefix=1`，保留 `/auth`；用户和订单路由 `StripPrefix=2`，转成 `/users`、`/orders`。

## Seata 范围

用户、订单服务使用 Seata 2.0.0 AT 客户端；订单创建、支付、取消显式标注 `@GlobalTransactional`。协调器使用独立 `seata` 库，客户端业务库需有 `undo_log`。

当前订单流程只有订单表写入，用户服务调用为读取；示例没有库存扣减、余额扣减或多个服务同时写库。`/api/order/seata-demo/test-commit` 调用创建订单；`test-rollback` 使用不存在的用户，在写库前抛出异常，演示该失败流程的全局事务回滚。

## 可选示例与当前边界

`luckyh-common-mq` 仍参与 Maven 构建，但四个应用没有依赖它，业务消息发送尚未启用，示例消费者不会随这四个服务启动。RabbitMQ 不是当前启动链路的必需组件。

SkyWalking Agent 和 OAP 未纳入当前部署；原先针对已删除公共模块的集成报告及启动脚本已清理。以后接入时使用实际 Agent 版本与部署环境单独验证。

当前业务接口主要验证登录态，尚未实现完整角色权限、订单归属和注册管理员限制。联系方式更新、分页参数和其他校验遵循现有 DTO、Service 和 MyBatis Plus 行为。
