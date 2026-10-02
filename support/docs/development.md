# 本地开发与启动

## 1. 首次环境准备

1. 安装 JDK 17、Maven 3.9，确认 `java -version`、`mvn -version` 使用同一 JDK。
2. 准备 Nacos、MySQL 和 Redis；本项目当前连接目标见 [README](../../README.md#当前环境连接目标)。
3. 按 [数据库说明](../sql/README.md) 准备 `luckyh_cloud`、`seata`，再执行 [03-distributed-demo.sql](../sql/03-distributed-demo.sql) 补齐库存、账户库。03 只创建缺失库 / 表、插入缺失种子，不重置已有余额或库存；应用账号需能访问三个业务库。
4. 按 [Seata 部署说明](../k8s/README.md) 准备协调器及控制台。
5. 按 [Nacos 配置说明](../nacos/README.md) 创建命名空间，发布业务配置和 Seata 映射。
6. 提供 Nacos 凭据，以及所需的配置环境变量，再启动应用。

仓库配置文件是后续环境配置的来源；修改本地文件后，需要发布相应 Nacos 配置才能供应用读取。

## 2. Windows 一键启动

在项目根目录执行：

```powershell
.\start-services.bat
```

启动脚本读取当前进程或 Windows 用户环境变量中的 `NACOS_USERNAME`、`NACOS_PASSWORD`，用户名默认 `nacos`。新机器可在当前 PowerShell 中填写凭据：

```powershell
$nacosCredential = Get-Credential -UserName 'nacos'
$env:NACOS_USERNAME = $nacosCredential.UserName
$env:NACOS_PASSWORD = $nacosCredential.GetNetworkCredential().Password
.\start-services.bat
```

脚本自动选取有默认网关的物理 IPv4 地址用于服务注册，设置 UTF-8 JVM 编码，构建根 POM，并依次启动：

| 顺序 | 模块 | 运行应用名 | 端口 |
| --- | --- | --- | ---: |
| 1 | luckyh-auth-service | auth-service | 8083 |
| 2 | luckyh-user-service | user-service | 8081 |
| 3 | luckyh-inventory-service | inventory-service | 8084 |
| 4 | luckyh-account-service | account-service | 8085 |
| 5 | luckyh-order-service | order-service | 8082 |
| 6 | luckyh-gateway-service | gateway-service | 8080 |

每个服务最多等待 120 秒达到健康状态 `UP`。重复启动会跳过本脚本已管理且健康的服务；部分服务已运行时跳过重新构建。

```powershell
# 停止由本脚本管理的服务
.\stop-services.bat
# 代码更新后重新构建并启动
.\start-services.bat
# 使用现有 JAR 启动
.\start-services.bat -SkipBuild
```

日志在 `.local/logs/`，进程记录在 `.local/run/`。停止脚本通过 PID、启动时间和 JAR 路径识别进程。IDEA 或其他终端启动的服务，使用对应入口停止。

## 3. IDEA 启动与调试

导入根目录 `pom.xml`，设置项目 SDK 为 JDK 17。已有 IDEA 项目在本次模块改名后，执行 Maven 工具窗口的 **Reload All Maven Projects**；原有运行配置重新选择改名后的模块 classpath，主类保持原值。创建或检查以下 Spring Boot 运行配置：

| 模块 | 主类 |
| --- | --- |
| luckyh-auth-service | `com.luckyh.cloud.auth.AuthServiceApplication` |
| luckyh-user-service | `com.luckyh.cloud.user.UserServiceApplication` |
| luckyh-inventory-service | `com.luckyh.cloud.inventory.InventoryServiceApplication` |
| luckyh-account-service | `com.luckyh.cloud.account.AccountServiceApplication` |
| luckyh-order-service | `com.luckyh.cloud.order.OrderServiceApplication` |
| luckyh-gateway-service | `com.luckyh.cloud.gateway.GatewayServiceApplication` |

六个配置都设置 VM options：`-Dfile.encoding=UTF-8`，并提供 `NACOS_USERNAME`、`NACOS_PASSWORD`。从 Windows 用户环境变量继承时，设置后完全退出并重启 IDEA。

多网卡机器设置 `SPRING_CLOUD_NACOS_DISCOVERY_IP` 为其他服务能访问的本机物理 IPv4；也可以设置 `SPRING_CLOUD_NACOS_DISCOVERY_NETWORK_INTERFACE` 为实际 Java 网卡名。网卡名和 IP 因机器而异。各服务的注册地址必须能互通。

按认证、用户、库存、账户、订单、网关顺序启动。避免与一键启动的实例占用同一端口。

## 4. Maven 构建

```powershell
# 构建并运行已有测试
mvn clean verify
# 只重新打包购买链路服务及其依赖
mvn -pl ':luckyh-user-service,:luckyh-inventory-service,:luckyh-account-service,:luckyh-order-service' -am package
```

每个服务的可执行 JAR 在其 `target/` 下。手工启动示例：

```powershell
java '-Dfile.encoding=UTF-8' -jar luckyh-auth-service/target/luckyh-auth-service-1.0.0.jar
```

Maven 构建不会初始化数据库、发布 Nacos 配置或部署 K8s。

## 5. 运行验证

```powershell
8080..8085 | ForEach-Object {
    $health = Invoke-RestMethod "http://localhost:$_/actuator/health"
    [pscustomobject]@{ Port = $_; Status = $health.status }
}
```

六个结果应为 `UP`。登录后经网关验证业务数据：

```powershell
$loginBody = @{ username = 'admin'; password = '123456' } | ConvertTo-Json
$login = Invoke-RestMethod 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body $loginBody
if ($login.code -ne 200) { throw $login.message }
$headers = @{ Authorization = 'Bearer ' + $login.data.accessToken }
Invoke-RestMethod 'http://localhost:8080/api/user/users?current=1&size=1' -Headers $headers
Invoke-RestMethod 'http://localhost:8080/api/order/orders?current=1&size=1' -Headers $headers
Invoke-RestMethod 'http://localhost:8080/api/inventory/inventory/1' -Headers $headers
Invoke-RestMethod 'http://localhost:8080/api/account/accounts/1' -Headers $headers
```

分页结果的 `data.records` 应最多 1 条，`data.total` 表示满足条件的总条数。使用 `current=2` 验证下一页；具体条数取决于当前数据库。

订单写入还需 Seata TM/RM 连接成功。通过订单页创建、支付或取消测试订单后，回读状态，并查看订单服务与协调器日志；单纯健康检查或 `/seata-demo/info` 的固定说明不能证明事务已成功运行。

独立购买接口写入订单、库存、账户三个库，测试提交、全写后回滚及故障场景请按 [跨服务事务测试指南](distributed-transactions.md) 逐次执行并比较 SQL。更新仓库文件不会自动执行 SQL、发布 Nacos 配置或更新运行环境。

当前 demo 的四个 Seata 客户端从 Nacos public / `SEATA_GROUP` / `seataServer.properties` 读取 `transport.heartbeat=false`、`transport.rpcTmRequestTimeout=30000`、`transport.rpcRmRequestTimeout=15000`（不带 `seata.` 前缀，后两项为毫秒），本地 Spring YAML 心跳项已删除。TC 的 file provider 使用清单中的 `seata.transport.heartbeat=false`。发布后必须重启四个客户端，核对 JVM 心跳 `false`、读 / 写空闲 `0 / 0` 和 RPC 超时；TC 配置调整也须重启，Nacos 刷新或只改单端不足以确认生效。

订单网关路由等待为 `60000` 毫秒，其他路由仍为 `30s`；TM / RM RPC 分别保持 30 秒 / 15 秒有限等待，重试总时长可能超过 60 秒。修复网络 / 存储延迟后同步恢复两端心跳并全部重启，详见 [兼容配置说明](distributed-transactions.md#当前-demo-的长回滚兼容处理)。2026-10-02 14:38–14:40 五个真实场景已通过三库核对；TC 与四个客户端实际 JVM 均为 `false / 0 / 0`，客户端 RPC 为 `30000ms / 15000ms`。约 14:41 账户恢复后六应用健康均 `UP`、Nacos 六服务健康注册均通过，见 [实测记录](distributed-transactions.md#8-本次实测记录)。

## 6. 前端联调

```powershell
cd D:\project\demo\luckyh-cloud-web
npm start
```

前端 <http://localhost:8000> 通过开发代理访问网关 `8080`。登录、业务用户和订单页面使用真实后端；Ant Design Pro 示例页沿用模板 Mock。具体前端启动说明见前端项目 `README.md`。

订单页面创建待支付订单，支付时由订单服务通过 Feign 调用库存、账户服务，并由 Seata 统一提交或回滚；“分布式事务演示”另提供直接购买与主动回滚入口。“商品管理”维护商品和补货，订单创建、购买与库存查询均可按名称选择商品。“余额管理”按登录用户管理账户余额。已有环境先按 [数据库升级步骤](../sql/README.md) 启用商品编号自增并为订单表增加商品 ID。
