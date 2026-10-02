# 常见问题

## Nacos 配置提示 does not exist

按顺序检查：

1. `server-addr` 使用客户端入口 `192.168.10.201:8848`，不是控制台 Ingress `nacos.home`；客户端还需能访问 `9848`。
2. `NACOS_USERNAME`、`NACOS_PASSWORD` 正确，账号能登录控制台。IDEA 启动前需继承最新环境变量。
3. 使用命名空间 **ID** `luckyh-cloud`、分组 `DEFAULT_GROUP`，Data ID 与各服务的 `spring.config.import` 完全一致。
4. 四个 Java 进程使用 `-Dfile.encoding=UTF-8`。Windows/JDK 17 曾因默认 GBK 解析中文 Nacos YAML 失败，底层 `MalformedInputException` 被包装成配置不存在。
5. 远端服务配置没有重新定义 Nacos 地址、凭据或 `spring.config.import`，避免覆盖本地连接设置。

必需配置采用非 optional 导入；先修复地址、鉴权或 YAML 错误。完整文件列表见 [Nacos 配置](../nacos/README.md)。

```powershell
Test-NetConnection 192.168.10.201 -Port 8848
Test-NetConnection 192.168.10.201 -Port 9848
```

## 数据库或 Redis 连接失败

- MySQL 实际端口是 `3380`，不是常见默认端口 `3306`。检查 `db-common.yml`、实际 Nacos 内容与数据库账号权限。
- `Unknown database 'luckyh_cloud'`：首次环境按 [SQL 说明](../sql/README.md) 初始化业务库；已有数据库先检查连接目标。
- 缺表或密码错误：核对八张业务表及演示种子。演示密码 `123456` 的 BCrypt 已写入统一初始化脚本；已有账号使用自己的密码。
- Redis 用于退出令牌黑名单；核对地址、端口、密码和库编号，服务存活还需实际能执行 Redis 命令。

## 服务注册成功，但网关或 Feign 调用失败

检查 Nacos 中四个服务的注册 IP 是否为可互通的物理地址。Tailscale、VPN 或 Mihomo 虚拟网卡可能使自动选择结果不适合当前 LAN。

一键脚本自动设置 `SPRING_CLOUD_NACOS_DISCOVERY_IP`；IDEA 手工启动需显式选择物理 IP 或正确网卡。地址调整后重启服务，核对旧实例是否已下线。

## Seata 连接失败或订单无法写入

1. 检查 `192.168.10.203:8091` 可达，Seata Pod Ready，服务端库已初始化。
2. 检查 Nacos public 命名空间、`SEATA_GROUP` 的 `seataServer.properties`。`default_tx_group` 映射为 `default`，对应协调器地址正确。
3. 用户、订单客户端使用 `registry.type=file` 和 `config.type=nacos`；协调器本身使用文件配置。
4. 业务库有 `undo_log`，Seata Secret 中数据库连接正确。

当前固定地址配置用于兼容 Seata 2.0.0 与现有 Nacos 3；协调器不出现在 Nacos 服务列表是当前预期行为。控制台 `seata.home` 的 Ingress 只提供网页访问，客户端事务走 `8091`。部署检查命令见 [K8s 说明](../k8s/README.md)。

## 端口冲突或脚本无法停止服务

```powershell
Get-NetTCPConnection -State Listen -LocalPort 8080,8081,8082,8083 |
    Select-Object LocalPort,OwningProcess
```

`stop-services.bat` 只停止本脚本有记录且身份匹配的进程。IDEA 或其他终端启动的进程应由原入口停止，再切换启动方式。

## 分页返回过多记录或 total 为 0

用户、订单服务均应加载各自的 `MybatisPlusConfig` MySQL 分页拦截器。更新后重新构建并重启对应服务，再用 `current=1&size=1` 和 `current=2&size=1` 验证。

## 前端登录或接口失败

先验证网关 `8080` 健康与真实登录接口，再检查前端代理目标。HTTP 200 仍需判断响应 `code`，401 需重新登录。模板示例 Mock 数据不能证明业务接口已连接成功。
