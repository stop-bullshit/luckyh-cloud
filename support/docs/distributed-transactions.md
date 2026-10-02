# 跨服务事务测试指南

本指南验证 Seata 2.0.0 AT 的三个写入分支：订单、库存、账户。第 1–7 节提供复现步骤与验收标准，第 8 节记录 2026-10-02 的真实执行结果；修改仓库文件不会自动执行 SQL、发布 Nacos 或启动测试。

## 1. 服务与数据边界

| 模块 | 应用 / Nacos 服务名 | 端口 | 用途 |
| --- | --- | ---: | --- |
| luckyh-auth-service | auth-service | 8083 | 登录、令牌校验 |
| luckyh-user-service | user-service | 8081 | 查询购买用户，购买流程只读 |
| luckyh-inventory-service | inventory-service | 8084 | 查询商品、扣减库存 |
| luckyh-account-service | account-service | 8085 | 查询账户、扣减余额 |
| luckyh-order-service | order-service | 8082 | 发起全局事务、写订单、调用库存和账户 |
| luckyh-gateway-service | gateway-service | 8080 | 认证与业务路由 |

| 写入方 | 数据库与表 | 本地 AT 日志 |
| --- | --- | --- |
| 订单 | `luckyh_cloud.order_info` | `luckyh_cloud.undo_log` |
| 库存 | `luckyh_inventory.inventory` | `luckyh_inventory.undo_log` |
| 账户 | `luckyh_account.account_balance` | `luckyh_account.undo_log` |

协调器使用独立 `seata` 库，事务入口为 `192.168.10.203:8091`，控制台为 [http://seata.home/](http://seata.home/)。登录账号 `admin` 与请求中的业务 `userId=1` 是不同数据，不要用登录账号 ID 推断业务用户 ID。

## 2. 准备数据库、Nacos 和应用

1. 按 [SQL 说明](../sql/README.md) 准备已有业务库及协调器库，再执行 [03-distributed-demo.sql](../sql/03-distributed-demo.sql)。它只创建缺失库 / 表、插入缺失种子，不删除数据、不重置库存和余额，也不修正已有表结构。
2. 应用数据库账号需能读写三个业务库，包括各自的 `undo_log`。数据库地址、端口、账号、密码和 JDBC 参数统一来自 `db-common.yml`；库存、账户配置只选择 `luckyh_inventory`、`luckyh_account` 数据库名。
3. 在 Nacos `luckyh-cloud` / `DEFAULT_GROUP` 新增 `inventory-service.yml`、`account-service.yml`，更新 `gateway-service.yml` 的两条路由。清单共八份业务 YAML，public / `SEATA_GROUP` 另有一份 `seataServer.properties`，总计九个 Data ID。完整内容见 [Nacos 配置](../nacos/README.md)。
4. 启动六个应用，检查健康、Nacos 注册 IP、TM/RM 到协调器的连接。更新仓库文件不会自动发布配置或执行 SQL。

安装了 MySQL CLI 的 PowerShell 可手工执行以下命令，账号按实际权限选择，密码由 `-p` 交互输入：

```powershell
mysql --host=192.168.10.13 --port=3380 --user=root -p --default-character-set=utf8mb4 --execute="source D:/project/demo/luckyh-cloud/support/sql/03-distributed-demo.sql"
if ($LASTEXITCODE -ne 0) { throw 'SQL03 执行失败，请检查错误后再启动服务。' }
```

03 首次插入的商品和账户如下。`INSERT IGNORE` 不覆盖已有值，每个测试前都要读取当前值。

| 商品 ID | 商品 | 单价 | 初始可用库存 |
| ---: | --- | ---: | ---: |
| 1 | Seata演示商品 | 99.90 | 1000 |
| 2 | 余额不足演示商品 | 500.00 | 1000 |
| 3 | 库存不足演示商品 | 9.90 | 0 |

业务用户 1 初始余额 `10000.00`，用户 2 初始余额 `10.00`；03 为用户 1–5 插入缺失账户。业务用户资料由 01 初始化脚本提供，购买时必须仍存在。

## 3. 登录与购买接口

网关业务接口要求 `Authorization: Bearer <accessToken>`。以下变量在同一个 PowerShell 窗口复用，不打印令牌；账号密码是初始化脚本的演示值，已修改时使用实际登录凭据。

```powershell
$demoGateway = 'http://localhost:8080'
$demoLoginBody = @{ username = 'admin'; password = '123456' } | ConvertTo-Json
$demoLogin = Invoke-RestMethod "$demoGateway/api/auth/login" -Method Post -ContentType 'application/json' -Body $demoLoginBody
if ($demoLogin.code -ne 200 -or -not $demoLogin.data.accessToken) { throw '登录失败。' }
$demoHeaders = @{ Authorization = 'Bearer ' + $demoLogin.data.accessToken }
```

| 方法 | 网关路径 | 行为 |
| --- | --- | --- |
| POST | `/api/order/orders/purchase` | 正常购买，创建已支付订单 |
| POST | `/api/order/seata-demo/test-commit` | 调用同一购买提交流程 |
| POST | `/api/order/seata-demo/test-rollback` | 三库写入完成后主动抛错 |
| GET | `/api/inventory/inventory/{productId}` | 查询商品价格和库存 |
| GET | `/api/account/accounts/{userId}` | 查询账户余额 |

三个 POST 的 JSON 均为 `{"userId":1,"productId":1,"quantity":1}`，ID 和数量必须为正。金额由服务端商品单价乘数量计算，调用方不能指定单价或金额。`purchase` / `test-commit` 成功返回的 `data` 为订单 ID；订单编号以 `PURCHASE_` 开头，状态为 `1` 已支付。

新增网关路由 `/api/inventory/**`、`/api/account/**` 均 `StripPrefix=2`。直接调用 `/api/inventory/inventory/deduct` 或 `/api/account/accounts/debit` 且没有有效事务 XID 会被拒绝；正常测试从订单入口发起，不手工伪造 `TX_XID`。

## 4. 每个场景的 SQL 基线与最终核对

使用独立的演示环境，测试期间不要同时运行其他购买请求。每个场景单独记录请求时间、输入、订单 ID（或回滚异常中的订单 ID）、XID、三库 before / after 和协调器最终结果。每次 POST 前重新取得基线，不沿用上一场景的库存或余额。

在同一 MySQL 连接 / SQL 编辑器会话执行下面的 **before**，按场景修改三个变量。保留这段输出和变量，在请求结束后继续执行 **after**。

```sql
SET @demo_user = 1;
SET @demo_product = 1;
SET @demo_quantity = 1;

SELECT product_id, product_name, product_price, available_quantity, update_time
FROM luckyh_inventory.inventory WHERE product_id = @demo_product;
SELECT user_id, balance, update_time
FROM luckyh_account.account_balance WHERE user_id = @demo_user;
SELECT COUNT(*) INTO @before_order_count
FROM luckyh_cloud.order_info
WHERE user_id = @demo_user AND order_no LIKE 'PURCHASE%';
SELECT product_price, available_quantity INTO @demo_price, @before_stock
FROM luckyh_inventory.inventory WHERE product_id = @demo_product;
SELECT balance INTO @before_balance
FROM luckyh_account.account_balance WHERE user_id = @demo_user;
SELECT @before_order_count AS before_order_count,
       @before_stock AS before_stock,
       @before_balance AS before_balance,
       @demo_price * @demo_quantity AS purchase_amount;
```

如果商品或账户不存在，先解决初始化问题；不要继续使用未取得的基线。请求返回后执行 **after**，在协调器处理完成后再确认最终值：

```sql
SELECT product_id, product_price, available_quantity, update_time
FROM luckyh_inventory.inventory WHERE product_id = @demo_product;
SELECT user_id, balance, update_time
FROM luckyh_account.account_balance WHERE user_id = @demo_user;
SELECT COUNT(*) AS after_order_count, @before_order_count AS before_order_count
FROM luckyh_cloud.order_info
WHERE user_id = @demo_user AND order_no LIKE 'PURCHASE%';
SELECT id, order_no, user_id, product_name, product_price, quantity,
       total_amount, status, create_time, update_time
FROM luckyh_cloud.order_info
WHERE user_id = @demo_user AND order_no LIKE 'PURCHASE%'
ORDER BY id DESC LIMIT 10;
SELECT @before_stock AS before_stock, @before_balance AS before_balance,
       @demo_price * @demo_quantity AS purchase_amount;
```

成功场景再按返回 ID 定位新订单；回滚场景按日志中的尝试订单 ID 核对该记录不存在。`order_info` 没有 `product_id` 字段，以订单 ID 为准，商品名 / 编号和上述计数只作辅助。回滚后可能留下 ID 间隙，不能用编号连续性判断失败。

同一 XID 在三个写服务和协调器日志中应能关联。将真实 XID 填入下面的只读检查：

```sql
SET @demo_xid = '替换为本次日志中的XID';
SELECT xid, branch_id, log_status
FROM luckyh_cloud.undo_log WHERE xid = @demo_xid;
SELECT xid, branch_id, log_status
FROM luckyh_inventory.undo_log WHERE xid = @demo_xid;
SELECT xid, branch_id, log_status
FROM luckyh_account.undo_log WHERE xid = @demo_xid;
SELECT xid, status, application_id, transaction_service_group
FROM seata.global_table WHERE xid = @demo_xid;
```

AT 日志和已结束的全局事务记录可能已被清理，空结果不能单独证明提交或回滚成功。结合三库最终值及协调器同一 XID 的提交 / 回滚完成日志；如果还在重试或处理，应等待完成再复查。HTTP 返回或 `/seata-demo/info` 的固定说明也不能代替这些证据。

## 5. 五个必测场景

每个示例只发送一次请求；发生 HTTP 异常时仍继续收集日志和 after SQL。PowerShell 会将 HTTP 500 报为错误，错误本身不代表回滚已完成。

### 5.1 正常提交：用户 1、商品 1、数量 1

先取第 4 节基线，确保库存和余额足够，然后执行：

```powershell
$demoBody = @{ userId = 1; productId = 1; quantity = 1 } | ConvertTo-Json
$demoPurchase = Invoke-RestMethod "$demoGateway/api/order/orders/purchase" -Method Post -Headers $demoHeaders -ContentType 'application/json' -Body $demoBody
$demoPurchase
```

若要验证演示提交接口，将路径替换为 `/api/order/seata-demo/test-commit`，二者任选其一；连续调用两者会产生两次购买。

验收：业务码 `200`，新订单存在且 `status=1`，金额等于基线单价 × 数量，订单数增加 1；库存减少 1，余额减少该金额，协调器提交完成。未使用过的种子数据示例为库存 `1000→999`、余额 `10000.00→9900.10`。

### 5.2 三库全写后回滚：用户 1、商品 1、数量 1

重新取得基线，保证余额和库存足够：

```powershell
$demoBody = @{ userId = 1; productId = 1; quantity = 1 } | ConvertTo-Json
Invoke-RestMethod "$demoGateway/api/order/seata-demo/test-rollback" -Method Post -Headers $demoHeaders -ContentType 'application/json' -Body $demoBody
```

验收：订单日志出现“三个购买分支操作完成”，随后主动抛出回滚异常；订单、库存和账户写入日志使用同一 XID。最终尝试订单不存在、订单数不变、库存和余额都恢复到本次基线，协调器回滚完成。

这个接口捕获预期的回滚异常后返回业务码 `200` 和“已触发全局回滚”说明；该成功消息只表示进入预期路径，仍必须核对最终数据。其他异常不会被这个预期异常处理掩盖。

### 5.3 库存不足：用户 1、商品 3、数量 1

before 设置 `@demo_user=1`、`@demo_product=3`，确认商品 3 当前库存为 0：

```powershell
$demoBody = @{ userId = 1; productId = 3; quantity = 1 } | ConvertTo-Json
Invoke-RestMethod "$demoGateway/api/order/orders/purchase" -Method Post -Headers $demoHeaders -ContentType 'application/json' -Body $demoBody
```

验收：订单已尝试写入；库存条件更新影响 0 行，库存服务返回 HTTP 200 / 业务码 `409`；订单检查结果后抛错，账户扣减未执行。最终新订单不存在、库存仍为 0、余额不变，协调器回滚完成。此业务失败不会触发库存 `FallbackFactory`。

### 5.4 余额不足：用户 2、商品 2、数量 1

before 设置 `@demo_user=2`、`@demo_product=2`，确认商品有库存且余额小于本次金额。新种子为价格 `500.00`、余额 `10.00`：

```powershell
$demoBody = @{ userId = 2; productId = 2; quantity = 1 } | ConvertTo-Json
Invoke-RestMethod "$demoGateway/api/order/orders/purchase" -Method Post -Headers $demoHeaders -ContentType 'application/json' -Body $demoBody
```

验收：订单写入、库存扣减均已完成；账户条件更新影响 0 行，账户返回 HTTP 200 / 业务码 `409`，订单抛错。最终新订单不存在、库存恢复到基线、账户余额不变，协调器回滚完成。此业务失败不会触发账户 `FallbackFactory`。

### 5.5 服务不可用：账户服务停止

在独立演示环境中，用对应 IDEA 运行配置停止 `account-service`，保留其他五个服务和协调器运行；若使用脚本管理实例，应先安排单实例停止方式，根停止脚本会停止整个应用组。确认没有其他健康账户实例可被负载均衡选中。

重新读取用户 1 / 商品 1 的数据库基线，发送一次正常购买请求，参数仍为 `{"userId":1,"productId":1,"quantity":1}`。账户调用应因无实例、连接失败、HTTP 错误或熔断而失败；服务发现缓存会影响具体异常类型。

验收：账户 `FallbackFactory` 日志包含原始 cause 堆栈、`debit` 方法、userId、amount 和 XID；订单收到非成功业务码后抛错。订单与库存已经写入的分支最终回滚，新订单不存在，库存 / 余额与基线一致，协调器回滚完成。恢复账户服务后检查注册和健康状态，再进行新的测试。

用户、库存客户端也使用各自的 `FallbackFactory`；用户查询或商品查询提前失败时，还没有三个写入分支，不能用这种失败替代全写后回滚测试。

## 6. XID、Feign 超时与降级实现

购买方法的 `@GlobalTransactional` 发起全局事务；库存、账户的具体 Service 方法使用本地 `@Transactional`，并检查 `RootContext.getXID()` 非空后才执行条件扣减。库存条件为数量足够，账户条件为余额足够，均由一条数据库 UPDATE 完成，不使用先查再扣或 Java 锁。

Seata 的默认上下文基于 ThreadLocal，跨服务参与者需要绑定同一个 XID；参见 [Seata 微服务事务说明](https://seata.apache.org/docs/v1.5/user/microservice/)。本项目通过已有 Spring Cloud Alibaba / Seata 集成自动传递 `TX_XID`，没有新增自定义拦截器。

订单 `application.yml` 配置如下，HTTP 超时分别为连接 2 秒、读取 5 秒，并关闭线程池及 bulkhead 隔离以保留调用线程上的 XID：

```yaml
spring:
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true
      client:
        config:
          default:
            connectTimeout: 2000
            readTimeout: 5000
    circuitbreaker:
      resilience4j:
        disable-thread-pool: true
      bulkhead:
        resilience4j:
          enabled: false
```

用户、库存、账户的降级工厂均记录 cause 堆栈、具体方法参数和 XID，返回失败而不伪造成功。订单显式检查 Feign 结果的业务码并抛出异常，确保进入回滚。OpenFeign 文档说明可通过 `fallbackFactory` 取得触发降级的异常；连接 / 读取超时也支持按客户端配置，见 [Spring Cloud OpenFeign 官方说明](https://docs.spring.io/spring-cloud-openfeign/reference/spring-cloud-openfeign.html)。

### 当前 demo 的长回滚兼容处理

前轮排查中，协调器已完成三个写库的回滚，但长回滚期间 TM 连接因读空闲检测关闭，响应未到达调用方，网关随后达到原有 30 秒超时。三库回滚完成与 HTTP 请求成功返回需要分别核对。现有证据不能把 TUN 地址 `198.18.0.1` 单独认定为根因。

Seata 2.0.0 的 `NettyBaseConfig` 在启用心跳时使用 5 秒写空闲、3 倍读空闲，即固定 15 秒读空闲；该值由静态初始化计算。见 [官方 v2.0.0 源码](https://raw.githubusercontent.com/apache/incubator-seata/v2.0.0/core/src/main/java/io/seata/core/rpc/netty/NettyBaseConfig.java)。`transport.heartbeat` 是客户端与服务端的通信心跳开关，官方默认开启，见 [Seata v2.0 参数说明](https://seata.apache.org/zh-cn/docs/v2.0/user/configurations/)。

当前仓库仅为此 demo 的长回滚同步关闭两端心跳：

| 位置 | 当前配置 | 生效要求 |
| --- | --- | --- |
| Nacos public / `SEATA_GROUP` / `seataServer.properties`，供用户、库存、账户、订单读取 | `transport.heartbeat=false`、`transport.rpcTmRequestTimeout=30000`、`transport.rpcRmRequestTimeout=15000` | 四个客户端全部重启，并核对 JVM 实际静态参数 |
| `support/k8s/seata.yml` 中 TC 的 ConfigMap 配置 | `seata.transport.heartbeat=false` | 应用清单并重启 TC，确认 rollout 完成 |
| Nacos `gateway-service.yml` 的订单路由 metadata | `response-timeout=60000`，单位毫秒 | 发布该 Data ID，确认网关加载新路由 |

客户端使用 Seata Nacos 配置 provider，实际 JVM 核对确认传输参数需从上述 public 配置读取；本地 Spring YAML 的心跳项已删除。`seataServer.properties` 的精确字段如下，均不带 `seata.` 前缀，后两项单位为毫秒：

```properties
transport.heartbeat=false
transport.rpcTmRequestTimeout=30000
transport.rpcRmRequestTimeout=15000
```

TC 使用 file provider，保留 `support/k8s/seata.yml` 中的 `seata.transport.heartbeat=false`。不要将客户端 properties 的字段前缀与 TC YAML 混用。

其他路由仍使用全局网关响应超时 `30s`。Seata RPC 保留有限等待：TM 30 秒、RM 15 秒；Feign 仍为连接 2 秒、读取 5 秒。这些分别约束不同调用，RPC 重试、分支处理和协调器重试可能累积更久，不能保证整个请求或回滚在 60 秒内完成。

传输配置含 JVM 静态初始化参数。Nacos 发布 / 刷新后仍必须重启四个客户端，用 JVM attach 或等效方式核对实际心跳为 `false`、`MAX_READ_IDLE_SECONDS=0`、`MAX_WRITE_IDLE_SECONDS=0`，再确认 RPC 超时为 TM `30000ms` / RM `15000ms`。TC 心跳调整也需要重启并核对相同的 `false / 0 / 0`；只修改一端或只看 Nacos 内容不足以确认生效。

当前 demo 使用此兼容处理期间，仍需修复网络 / 存储延迟。修复后将客户端 public 配置的 `transport.heartbeat` 与 TC YAML 的 `seata.transport.heartbeat` 同步恢复为 `true`，重启 TC 和四个客户端，再按五个场景核对响应、三库最终值和协调器状态。关闭心跳期间，连接故障仍需结合有限 RPC 超时和日志发现。

### 本次验证进度（2026-10-02）

- 已实际执行并通过 17 个测试：16 个新增测试和 1 个已有 common-core 测试；6 份测试 XML 报告合计 errors / failures / skipped 均为 0。
- 根项目 12 个 Maven 模块已完成打包；最后一次仅配置变更的打包使用 `skipTests`，上述 17 个测试在此前单独实跑，打包结果不替代测试结果。
- TC Ready，TC 与四个客户端实际 JVM 心跳 / 读空闲 / 写空闲均为 `false / 0 / 0`，客户端 TM / RM RPC 均为 `30000ms / 15000ms`；账户恢复后再次核对一致。
- 14:38–14:40，五个真实场景已通过三库数据核对。约 14:41 账户恢复后，六应用 `8080–8085` 健康均为 `UP`，Nacos 六服务健康注册均通过；具体记录见第 8 节。
- 108 个 Java / POM / application 源码配置文件与已通过构建的快照 SHA 一致。

接口仍使用“已触发全局回滚，请核对订单、库存、余额与协调器状态”的说明，不把触发消息改写为回滚成功证据。

## 7. 使用边界与记录

订单页创建待支付订单时保存商品 ID；支付接口在同一个 Seata 全局事务中依次更新订单状态、通过 Feign 扣减库存和扣减账户余额，任一分支失败都会抛出异常并触发全局回滚。退款接口使用新的全局事务退回账户余额和库存，并将已支付订单更新为已退款。支付和退款均使用带原状态条件的更新，避免重复扣款或重复退款。历史订单缺少商品 ID 时拒绝支付和库存返还，避免按商品名称猜测并操作错误商品。

三库购买入口仍可直接生成已支付订单。当前退款为整单全额退款，不支持部分退款；网络异常时由 Seata 统一回滚，不额外增加自动补偿重试。

不要重复发送测试购买请求。网络超时后先按 XID 和订单数据确认最终结果，再决定下一次操作；不要因为没看到成功消息就再次购买。验证结束后保留每个场景的三库 before / after、服务日志与协调器最终结果，不用接口消息代替证据。

## 8. 本次实测记录

实测时间：**2026-10-02 14:38–14:40（Asia/Shanghai）**。每个场景先取得独立基线，再检查订单、库存、余额和同一 XID 的最终日志。订单 TM 于 `14:37:20` 注册成功，首次事务于 `14:38:38` 发起，TM 于 `14:38:54` 记录 `Committed`，注册后空闲 78 秒仍正常工作；该结果只证明本次运行中的这段空闲与请求链路。

下表 XID 的统一前缀为 `192.168.10.203:8091:320739232289468`，完整 XID 由前缀直接拼接四位后缀。购买订单数是 JSON 证据中的 **全库 `purchaseOrders`**，不是第 4 节按业务用户筛选的计数。

| 场景（userId / productId / quantity） | XID 后缀 / 尝试订单 ID | 全库购买订单数 | 对应商品库存 | 对应用户余额 | 分支数 / 协调器最终结果 |
| --- | --- | --- | --- | --- | --- |
| 正常提交（1 / 1 / 1） | `0065` / `13` | `2→3` | 商品 1：`998→997` | 用户 1：`9800.20→9700.30` | 3 / `Committed`（14:38:58） |
| 全写后主动回滚（1 / 1 / 2） | `0082` / `14` | `3→3` | 商品 1：`997→997` | 用户 1：`9700.30→9700.30` | 3 / `Rollbacked`（14:39:15） |
| 库存不足（1 / 3 / 1） | `0101` / `15` | `3→3` | 商品 3：`0→0` | 用户 1：`9700.30→9700.30` | 1 / `Rollbacked`（14:39:24） |
| 余额不足（2 / 2 / 1） | `0114` / `16` | `3→3` | 商品 2：`1000→1000` | 用户 2：`10.00→10.00` | 2 / `Rollbacked`（14:39:41） |
| 账户不可用（1 / 1 / 1） | `0175` / `17` | `3→3` | 商品 1：`997→997` | 用户 1：`9700.30→9700.30` | 2 / `Rollbacked`（14:40:16） |

- 提交订单 ID 为 13，金额 `99.90`、状态已支付。后四次尝试订单均回滚，全库购买订单数保持 3；其余未涉及的库存与余额也与各自基线一致。
- 主动回滚实际使用数量 2，日志记录三库分支均已完成、金额 `199.80`，随后回滚；返回记录为 `200`。库存不足、余额不足、账户不可用的响应记录均为 `500`，结合数据库与最终事务状态判断结果。
- 账户不可用实际进入 `AccountServiceFallbackFactory`，日志保留 `debit`、userId、amount、XID 及 `SocketTimeoutException: Connect timed out` 原始堆栈。恢复账户后重新核对 JVM 参数和全部服务健康。
- TM 对 `0065` 明确记录 `Committed`，对其余四个 XID 明确记录 `Rollbacked`。TC 对提交事务注册 3 个分支，`14:38:58` 完成全局提交，三个分支均为 `PhaseTwo_Committed`；四个回滚事务分别完成 3 / 1 / 2 / 2 个分支的回滚。
- TC 最终快照显示单副本 Ready、Pod 重启数 0、实际 JVM 心跳 / 读空闲 / 写空闲为 `false / 0 / 0`；`14:36–14:42:12` 的 TC 日志窗口中 ERROR / WARN 均为 0。该范围仅表示本次采集窗口。

本机原始证据保存在 `D:\project\demo\luckyh-cloud\.local\distributed-verify-evidence.json`（前四场景）、`.local\distributed-outage-evidence.json`（账户不可用）、`.local\logs\luckyh-order-service.out.log`（XID 与 TM 最终状态）、`.local\seata-probe\tc-final-evidence.json`（TC 最终状态、分支数与 JVM / Pod 快照）、`.local\distributed-health.log`（Nacos 健康注册）。`.local/` 被 Git 忽略，以上表格与摘要已保留共享所需的结果，阅读本指南无需访问这些本机文件。

本次验证通过后仍保留第 6 节的 demo 心跳关闭边界；结果不代表网络 / 存储延迟已永久修复，也不保证后续任意 RPC 重试能在 60 秒内结束。
