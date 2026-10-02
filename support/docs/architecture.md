# 模块与业务说明

根 `pom.xml` 聚合 `luckyh-common/` 和六个 `luckyh-*-service/` 应用模块，模块保持根目录层级。`support/` 存放开发文档、初始化 SQL、Nacos 配置、K8s 清单和启停脚本，不参与 Maven 模块构建。

## 服务与公共模块

| 模块（目录 / Maven 名称） | 运行应用名 | 职责 | 直接使用的公共模块 |
| --- | --- | --- | --- |
| luckyh-gateway-service | gateway-service | WebFlux 网关、路由、跨域、调用认证服务校验令牌 | 无 |
| luckyh-auth-service | auth-service | 账号、注册、登录、JWT、角色权限查询、Redis 黑名单 | luckyh-common-core、luckyh-common-redis |
| luckyh-user-service | user-service | 业务用户资料 CRUD 与分页 | luckyh-common-core、luckyh-common-web |
| luckyh-inventory-service | inventory-service | 商品查询、条件更新扣减库存 | luckyh-common-core、luckyh-common-web |
| luckyh-account-service | account-service | 账户查询、增量充值、条件更新扣减余额 | luckyh-common-core、luckyh-common-web |
| luckyh-order-service | order-service | 订单 CRUD，Feign 编排用户、库存、账户与三库购买事务 | luckyh-common-core、luckyh-common-web |
| luckyh-common | — | 聚合四个公共子模块的 POM | luckyh-common-core、luckyh-common-web、luckyh-common-redis、luckyh-common-mq |
| luckyh-common-core | — | `Result`、`R`、基础常量和工具 | 无业务模块依赖 |
| luckyh-common-web | — | Servlet 用户上下文、读取请求头的拦截器 | 面向 MVC 服务 |
| luckyh-common-redis | — | Redis 操作、令牌黑名单 | 面向 Redis 使用方 |
| luckyh-common-mq | — | RabbitMQ 配置、消息类型、生产者与消费者示例 | 独立示例 |

目录名、Maven `artifactId` 和项目 `name` 使用左列名称；`spring.application.name` 与 Nacos 注册名使用运行应用名，六份服务配置的 Data ID 为运行应用名加 `.yml`。加上 `db-common.yml` 和 `common.yml`，共有八份业务 YAML；public / `SEATA_GROUP` 另有一份 `seataServer.properties`。Java 包名继续使用 `com.luckyh.cloud.*`。

DTO、VO、Entity、Mapper、Service 属于各自业务服务。订单 Feign 客户端及其配置属于 `luckyh-order-service`。网关使用 WebFlux，不能引入 Servlet 的 `luckyh-common-web`。

用户、库存、账户、订单普通响应为 `Result`；登录保留 `R`，额外序列化 `success`、`fail`。二者都有 `code`、`message`、`data`，成功业务码为 `200`。接口可能以 HTTP 200 返回业务失败，调用方需检查 `code`；网关拒绝认证时返回 HTTP 401。

## 数据归属

| 库 | 表 | 使用方 |
| --- | --- | --- |
| luckyh_cloud | `sys_user`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission` | luckyh-auth-service |
| luckyh_cloud | `user` | luckyh-user-service |
| luckyh_cloud | `order_info` | luckyh-order-service |
| luckyh_cloud | `undo_log` | Seata AT 客户端 |
| luckyh_inventory | `inventory`、`undo_log` | luckyh-inventory-service 与其 AT 分支 |
| luckyh_account | `account_balance`、`undo_log` | luckyh-account-service 与其 AT 分支 |
| seata | `global_table`、`branch_table`、`lock_table`、`distributed_lock` | Seata 协调器 |

订单和账户统一关联登录账号 `sys_user`。订单服务通过认证服务查询用户，前端用户选择器也统一读取 `/api/auth/users`；原 `user` 表及用户服务保留，但不再作为订单、购买和余额页面的用户来源。认证、用户、订单仍使用 `luckyh_cloud`；库存、账户分别使用独立业务库。购买写入 `luckyh_cloud.order_info`、`luckyh_inventory.inventory`、`luckyh_account.account_balance` 三个数据库。

## 调用流程

登录由网关转发到认证服务，认证服务读取账号及角色权限，验证 BCrypt 密码并签发 JWT。业务请求带 `Authorization: Bearer <accessToken>`；网关通过服务发现调用认证服务校验令牌及 Redis 黑名单，再转发业务请求。退出将令牌加入 Redis 黑名单。

`luckyh-common-web` 从 `X-User-*` 请求头读取上下文并在请求结束后清理；当前网关的令牌校验不自动填充这些头，不能把上下文组件视为已完成的权限或归属校验。

订单创建先通过 Feign 向认证服务查询登录用户，再按商品 ID 向库存服务查询商品名称和单价，计算金额、生成订单编号并保存 `order_info`。订单分页收集当前页用户 ID 后一次批量调用认证服务，避免逐单 Feign。状态为：`0` 待支付、`1` 已支付、`2` 已取消、`3` 已退款。支付在 Seata 全局事务中更新订单状态，并通过 Feign 调用库存、账户服务完成扣库存和扣余额；退款反向退回余额和库存。每次状态变化写入 `order_operation_log` 并记录 XID。

独立的购买入口只接收 `userId`、`productId`、`quantity`：查询用户和商品，按服务端商品单价计算金额，写入已支付的 `PURCHASE_` 订单，再扣库存、扣余额。普通订单支付与该入口复用同一组库存、账户 Feign 接口。任何分支失败都抛出异常，由 Seata 回滚已完成的写入。

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
| GET | `/api/auth/users` | 登录用户分页，查询 `sys_user` |
| GET | `/api/auth/users/{id}` | 登录用户详情，不返回密码 |
| POST | `/api/auth/users` | 新建登录用户并分配用户类型对应角色 |
| PUT | `/api/auth/users/{id}` | 更新登录用户资料、状态和用户类型 |
| DELETE | `/api/auth/users/{id}` | 删除登录用户，禁止删除当前登录账户 |
| GET | `/api/inventory/inventory/{productId}` | 商品及可用库存 |
| POST | `/api/inventory/inventory/deduct` | 事务分支扣库存，productId、quantity，要求有效 XID |
| GET | `/api/account/accounts/{userId}` | 账户余额 |
| POST | `/api/account/accounts/{userId}/recharge` | 首次充值创建账户，已有账户增量充值，amount 最多两位小数 |
| POST | `/api/account/accounts/debit` | 事务分支扣余额，userId、amount，要求有效 XID |
| GET | `/api/order/orders` | 订单分页，current、size、userId |
| GET | `/api/order/orders/{id}` | 订单详情 |
| POST | `/api/order/orders` | 创建订单，userId、productName、productPrice、quantity |
| POST | `/api/order/orders/{id}/pay` | 演示支付 |
| POST | `/api/order/orders/{id}/cancel` | 取消待支付订单 |
| POST | `/api/order/orders/{id}/refund` | 已支付订单全额退款并返还库存 |
| POST | `/api/order/orders/purchase` | 三库购买并生成已支付订单，userId、productId、quantity |
| POST | `/api/order/seata-demo/test-commit` | 同一购买流程的提交演示，参数同 purchase |
| POST | `/api/order/seata-demo/test-rollback` | 三库写入完成后主动抛错，参数同 purchase |
| GET | `/api/order/seata-demo/info` | 固定集成说明，不能代替运行验证 |

认证路由 `StripPrefix=1`，保留 `/auth`；用户、库存、账户和订单路由 `StripPrefix=2`。例如 `/api/inventory/inventory/1` 转成 `/inventory/1`，`/api/account/accounts/1` 转成 `/accounts/1`。

## Seata 范围

用户、库存、账户、订单服务使用 Seata 2.0.0 AT 客户端；订单创建、支付、取消和两个购买方法显式标注 `@GlobalTransactional`。购买时用户服务只读，订单、库存、账户各形成写入分支，每个写库都有 `undo_log`，协调器使用独立 `seata` 库。

库存、账户采用数据库条件更新和本地 `@Transactional`，写入前检查 `RootContext.getXID()`，禁止脱离全局事务扣减。Feign 使用框架已有的 `TX_XID` 传递机制，没有新增自定义拦截器。三个 Feign 客户端都使用 `FallbackFactory` 记录原始异常、方法参数和 XID，并返回业务失败；订单检查失败结果后抛异常。

`test-rollback` 在三库写入都完成后主动抛错；接口消息仅表示触发了回滚。最终提交或回滚必须检查三库前后数据和协调器状态。完整步骤见 [跨服务事务测试](distributed-transactions.md)。

当前 demo 四个客户端的 Nacos provider 从 public / `SEATA_GROUP` / `seataServer.properties` 读取传输参数：`transport.heartbeat=false`、`transport.rpcTmRequestTimeout=30000`、`transport.rpcRmRequestTimeout=15000`，不带 `seata.` 前缀，后两项单位毫秒。本地 Spring YAML 心跳项已删除；TC 的 file provider 使用清单中的 `seata.transport.heartbeat=false`。参数含静态初始化值，两端须重启并核对 JVM，Nacos 刷新不足以确认生效；网络 / 存储延迟修复后两端同步恢复心跳并全部重启。

订单路由响应等待为 60 秒，其他网关路由为 30 秒；有限 TM / RM RPC 等待与重试不等于整个事务的时长上限。2026-10-02 14:38–14:40 的五个场景已通过三库核对，TC 与四个客户端 JVM 心跳 / 读空闲 / 写空闲均为 `false / 0 / 0`，客户端 TM / RM RPC 为 `30000ms / 15000ms`。账户恢复后六应用健康与 Nacos 注册均正常，详细数据见 [实测记录](distributed-transactions.md#8-本次实测记录)。

## 可选示例与当前边界

`luckyh-common-mq` 仍参与 Maven 构建，但六个应用没有依赖它，业务消息发送尚未启用，示例消费者不会随这些服务启动。RabbitMQ 不是当前启动链路的必需组件。

SkyWalking Agent 和 OAP 未纳入当前部署；原先针对已删除公共模块的集成报告及启动脚本已清理。以后接入时使用实际 Agent 版本与部署环境单独验证。

当前业务接口主要验证登录态，尚未实现完整角色权限、订单归属和注册管理员限制。联系方式更新、分页参数和其他校验遵循现有 DTO、Service 和 MyBatis Plus 行为。

购买接口没有幂等重试、库存退款或余额退款；已有取消接口只针对待支付订单。网络超时后先核对最终数据，再决定后续操作，不能直接重复购买。
