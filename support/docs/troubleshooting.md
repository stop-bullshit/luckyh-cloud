# 常见问题

## Nacos 配置提示 does not exist

按顺序检查：

1. `server-addr` 使用客户端入口 `192.168.10.201:8848`，不是控制台 Ingress `nacos.home`；客户端还需能访问 `9848`。
2. `NACOS_USERNAME`、`NACOS_PASSWORD` 正确，账号能登录控制台。IDEA 启动前需继承最新环境变量。
3. 使用命名空间 **ID** `luckyh-cloud`、分组 `DEFAULT_GROUP`，Data ID 与各服务的 `spring.config.import` 完全一致。
4. 六个 Java 进程使用 `-Dfile.encoding=UTF-8`。Windows/JDK 17 曾因默认 GBK 解析中文 Nacos YAML 失败，底层 `MalformedInputException` 被包装成配置不存在。
5. 远端服务配置没有重新定义 Nacos 地址、凭据或 `spring.config.import`，避免覆盖本地连接设置。

必需配置采用非 optional 导入；先修复地址、鉴权或 YAML 错误。完整文件列表见 [Nacos 配置](../nacos/README.md)。

```powershell
Test-NetConnection 192.168.10.201 -Port 8848
Test-NetConnection 192.168.10.201 -Port 9848
```

## 数据库或 Redis 连接失败

- MySQL 实际端口是 `3380`，不是常见默认端口 `3306`。检查 `db-common.yml`、实际 Nacos 内容与数据库账号权限。
- `Unknown database 'luckyh_cloud'`：首次环境按 [SQL 说明](../sql/README.md) 初始化业务库；已有数据库先检查连接目标。
- `Unknown database 'luckyh_inventory'` / `'luckyh_account'`：执行 [03-distributed-demo.sql](../sql/03-distributed-demo.sql) 补缺，再核对各服务配置的数据源 URL。03 不修改已有表结构、库存或余额。
- 缺表或密码错误：核对八张业务表及演示种子。演示密码 `123456` 的 BCrypt 已写入统一初始化脚本；已有账号使用自己的密码。
- 购买链路还需 `luckyh_inventory.inventory`、`luckyh_account.account_balance`，以及两个新库各自的 `undo_log`；同一应用账号需有这两个库的读写权限。
- Redis 用于退出令牌黑名单；核对地址、端口、密码和库编号，服务存活还需实际能执行 Redis 命令。

## 服务注册成功，但网关或 Feign 调用失败

检查 Nacos 中六个服务的注册 IP 是否为可互通的物理地址。Tailscale、VPN 或 Mihomo 虚拟网卡可能使自动选择结果不适合当前 LAN。

一键脚本自动设置 `SPRING_CLOUD_NACOS_DISCOVERY_IP`；IDEA 手工启动需显式选择物理 IP 或正确网卡。地址调整后重启服务，核对旧实例是否已下线。

## Seata 连接失败或订单无法写入

1. 检查 `192.168.10.203:8091` 可达，Seata Pod Ready，服务端库已初始化。
2. 检查 Nacos public 命名空间、`SEATA_GROUP` 的 `seataServer.properties`。`default_tx_group` 映射为 `default`，对应协调器地址正确。
3. 用户、库存、账户、订单客户端使用 `registry.type=file` 和 `config.type=nacos`；协调器本身使用文件配置。
4. 三个写库 `luckyh_cloud`、`luckyh_inventory`、`luckyh_account` 各有 `undo_log`，Seata Secret 中协调器数据库连接正确。

当前固定地址配置用于兼容 Seata 2.0.0 与现有 Nacos 3；协调器不出现在 Nacos 服务列表是当前预期行为。控制台 `seata.home` 的 Ingress 只提供网页访问，客户端事务走 `8091`。部署检查命令见 [K8s 说明](../k8s/README.md)。

## 购买失败、XID 缺失或降级未回滚

- 库存与余额写入口拒绝没有 `RootContext` XID 的调用。正常入口是订单服务的购买或事务演示方法，由框架的 `TX_XID` 拦截器跨服务传递；不要通过手工伪造 XID 绕过检查。
- 核对订单配置：Feign 连接超时 `2000ms`、读取超时 `5000ms`，`spring.cloud.circuitbreaker.resilience4j.disable-thread-pool=true`，`spring.cloud.circuitbreaker.bulkhead.resilience4j.enabled=false`。异步线程隔离会影响默认 ThreadLocal 事务上下文。
- 服务连接失败、HTTP 500 或熔断可进入 `FallbackFactory`。查看原始 cause 堆栈、方法参数和 XID；HTTP 200 的业务码 409 不会触发降级工厂，订单通过检查业务码主动抛错回滚。
- `test-rollback` 的成功消息只表示主动回滚路径已触发。核对三个库的最终数据和对应 XID 的协调器日志；超时、异常或健康检查也不能单独证明回滚完成。步骤见 [跨服务事务测试](distributed-transactions.md)。

## 长回滚后网关超时，但数据库已恢复

前轮排查发现 TC 已完成三库回滚，而 TM 连接在长回滚期间被 Seata 2.0.0 的固定 15 秒读空闲检测关闭，回包未到达调用方，网关达到原有 30 秒响应超时。先按同一 XID 检查三库和 TC 最终状态，不要因 HTTP 超时重复发送购买。不能将 TUN 地址 `198.18.0.1` 单独视为已证实根因。

当前 demo 四个客户端使用 Seata Nacos provider，传输参数由 public / `SEATA_GROUP` / `seataServer.properties` 提供：`transport.heartbeat=false`、`transport.rpcTmRequestTimeout=30000`、`transport.rpcRmRequestTimeout=15000`，不带 `seata.` 前缀，后两项单位毫秒。本地 Spring YAML 心跳项已删除；TC 使用 file provider，在清单中保留 `seata.transport.heartbeat=false`。

四个客户端必须在发布后全部重启，并核对 JVM 实际心跳 `false`、读 / 写空闲 `0 / 0` 和 RPC 超时，Nacos 刷新不足以更新静态参数。TC 同样需要配置一致并重启。订单路由使用 metadata `response-timeout=60000`（毫秒），其他路由继续使用全局 `30s`。TM / RM RPC 仍分别保留 30 秒 / 15 秒有限等待；重试总时长不保证小于等于 60 秒。

这是当前环境的长回滚兼容处理。修复网络 / 存储延迟后，将客户端 public 配置的 `transport.heartbeat` 与 TC YAML 的 `seata.transport.heartbeat` 同步恢复为 `true` 并全部重启，再复验五个场景。2026-10-02 14:38–14:40，当前兼容配置下五个场景已通过三库核对；TC 与四个客户端实际 JVM 为 `false / 0 / 0`，客户端 TM / RM RPC 为 `30000ms / 15000ms`。账户恢复后六应用健康与 Nacos 注册正常。本次通过不代表网络 / 存储延迟已永久修复。配置与官方依据见 [事务指南](distributed-transactions.md#当前-demo-的长回滚兼容处理)，TC 重启步骤见 [K8s 说明](../k8s/README.md#修改配置后生效)。

## 端口冲突或脚本无法停止服务

```powershell
Get-NetTCPConnection -State Listen -LocalPort 8080,8081,8082,8083,8084,8085 |
    Select-Object LocalPort,OwningProcess
```

`stop-services.bat` 只停止本脚本有记录且身份匹配的进程。IDEA 或其他终端启动的进程应由原入口停止，再切换启动方式。

## 分页返回过多记录或 total 为 0

用户、订单服务均应加载各自的 `MybatisPlusConfig` MySQL 分页拦截器。更新后重新构建并重启对应服务，再用 `current=1&size=1` 和 `current=2&size=1` 验证。

## 前端登录或接口失败

先验证网关 `8080` 健康与真实登录接口，再检查前端代理目标。HTTP 200 仍需判断响应 `code`，401 需重新登录。模板示例 Mock 数据不能证明业务接口已连接成功。
