# LuckyH Cloud 应用部署

部署入口是同目录的 [application.yml](application.yml)。它包含 Kubernetes 命名空间 `luckyh-cloud`、运行 ConfigMap、六个 Java 应用、前端及 Ingress，每个应用初始一个副本。数据库、Nacos、Redis、RabbitMQ 和 Seata 使用已有服务。

## 镜像与端口

| Deployment / Service | 镜像 | HTTP 端口 | 内存 request / limit |
| --- | --- | --- | --- |
| `gateway-service` | `ghcr.io/stop-bullshit/luckyh-gateway-service` | 8080 | 192Mi / 640Mi |
| `auth-service` | `ghcr.io/stop-bullshit/luckyh-auth-service` | 8083 | 192Mi / 640Mi |
| `user-service` | `ghcr.io/stop-bullshit/luckyh-user-service` | 8081 | 192Mi / 640Mi |
| `order-service` | `ghcr.io/stop-bullshit/luckyh-order-service` | 8082 | 256Mi / 768Mi |
| `inventory-service` | `ghcr.io/stop-bullshit/luckyh-inventory-service` | 8084 | 192Mi / 640Mi |
| `account-service` | `ghcr.io/stop-bullshit/luckyh-account-service` | 8085 | 192Mi / 640Mi |
| `web` | `ghcr.io/stop-bullshit/luckyh-cloud-web` | 80 | 32Mi / 128Mi |

本次后端版本是 `sha-7a4e0469efb6cf107171606fdd7fd312d432ff75`，前端版本是 `sha-ca1c2794fb47495b58311cfc69ba1530a9f576c8`。清单直接固定这两个版本，更新时替换对应镜像标签。

七个应用总 request 为 **475m CPU / 1248Mi 内存**。Java 默认堆为 64–192Mi，订单为 64–256Mi；限制 Direct Memory、Metaspace，并以 `ActiveProcessorCount=2` 控制按 CPU 数量创建的线程。容器内存包含堆、类元数据、线程栈及 native memory，实际 RSS 会高于堆大小。

当前三个节点已有较多中间件，部署时应分批启动并观察节点可用内存。当前 `RollingUpdate` 使用 `maxSurge=1`、`maxUnavailable=0`，更新时会在旧 Pod 仍运行的情况下创建新 Pod，必须保留新增进程的实际内存；正在退出的 Pod 也仍占用内存。低 request 只能影响调度预留，不能增加实际容量。

### 本次应用调度选择

`k8s-node-02` 已运行两个 Nacos Pod。订单首次调度到该节点并启动时，节点可用内存仅约 149Mi，因此取消了尚未 Ready 的首次订单 Pod。当前清单对 `order-service`、`gateway-service` 增加 required node affinity：主机名 `NotIn [k8s-node-02]`，将这两个应用限定到 master 或 `k8s-node-01`；订单重新调度到 `k8s-node-01` 后就绪，七个应用现已 Ready。该限制来自本次节点容量检查，扩容或重新分配 Nacos 后应重新评估。

### 单副本更新的容量

当前容量下，`maxSurge=1` 有实际的更新风险：旧 Java Pod 未退出前，新 Pod 需要另一份完整 RSS。调度只预留 request，不能保证节点能够承受两个进程；订单和网关又不能使用 `k8s-node-02`，可选择的节点更少。低 request、限制堆大小或逐服务更新都不能消除同一个服务的新旧进程重叠。

最小建议是在能够接受短暂服务中断的维护窗口，将 Java Deployment 更新策略改为 `Recreate`，等待旧 Pod 完全退出后再创建新 Pod，更新前避开正在执行的订单事务。若要求持续可用，应先为允许调度的节点补足新增 Pod 的实际内存，再保留滚动更新。仅改成 `maxSurge=0`、`maxUnavailable=1` 仍可能存在旧 Pod 退出期间的资源重叠。[Kubernetes 更新策略说明](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#strategy)

本次清单仍保留原有 `RollingUpdate`；这里记录后续更新建议。

### 本次 Nacos 内存调整

部署准备时三个 Nacos Pod 各使用约 975Mi RSS，应用部署前的节点可用内存合计约 2.2GiB。本次通过 `kubectl set env` 将 Nacos JVM 调整为：`JVM_XMS=128m`、`JVM_XMX=384m`、`JVM_XMN=128m`、`JVM_MS=96m`、`JVM_MMS=256m`，等待 StatefulSet 滚动更新完成后，对 Nacos 配置 API 做整体验收。后续调整建议在每个 Pod 更新并通过 API 就绪检查后，再继续更新下一个。

原 StatefulSet 已备份在 master 的 `/tmp/luckyh-application/nacos-before-memory.yaml`。恢复时从备份读取 Nacos 容器的原五个 JVM 环境变量，用 `kubectl -n nacos set env statefulset/nacos` 将这些变量恢复，并等待 `kubectl -n nacos rollout status statefulset/nacos --timeout=600s`。只恢复这五项，保留当前凭据及数据卷；先确认节点容量能承受原 JVM 配置。

## Nacos 配置与集群地址

在 Nacos 中新建 **ID 为 `luckyh-cloud-k8s`** 的命名空间。把 [../nacos](../nacos/README.md) 的以下八个业务配置发布到该命名空间的 `DEFAULT_GROUP`：

`db-common.yml`、`common.yml`、`gateway-service.yml`、`auth-service.yml`、`user-service.yml`、`order-service.yml`、`inventory-service.yml`、`account-service.yml`。

此 Nacos 命名空间同时用于配置读取与服务注册，和本地 IDEA 使用的 `luckyh-cloud` 隔离。Deployment 通过 Downward API 将 `SPRING_CLOUD_NACOS_DISCOVERY_IP` 设置为 Pod IP，注册时使用应用原有名称，例如 `auth-service`。

运行 ConfigMap 使用以下连接参数：

| 环境变量 | 集群中的值 / 用途 |
| --- | --- |
| `NACOS_SERVER_ADDR` | `nacos.nacos.svc.cluster.local:8848` |
| `NACOS_NAMESPACE` | `luckyh-cloud-k8s` |
| `NACOS_GROUP` | `DEFAULT_GROUP` |
| `SEATA_NACOS_SERVER_ADDR` | `nacos.nacos.svc.cluster.local:8848` |
| `DB_HOST` / `DB_PORT` | 当前 MySQL `192.168.10.209:3306` |
| `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT` | `redis-api.redis.svc.cluster.local:6379`，保留代理连接方式 |
| `SPRING_RABBITMQ_HOST` / `SPRING_RABBITMQ_PORT` | `rabbitmq.rabbitmq.svc.cluster.local:5672` |
| `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE` / `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | 1 / 5 |

Pod 中的环境变量覆盖业务配置内的 Windows hosts 域名。默认未设置 `SPRING_DATA_REDIS_CLUSTER_NODES`，因此应用继续使用 Redis 代理。

认证、用户、订单使用 `luckyh_cloud`；库存和账户分别使用 `luckyh_inventory`、`luckyh_account`。先确认 Pod 能连接 MySQL、应用账号有对应库权限、Seata 参与的业务库存在 `undo_log`，沿用已有数据。

用户、订单、库存、账户的 Seata 客户端继续读取 Nacos **public（空 ID）/`SEATA_GROUP`/`seataServer.properties`**。保留现有 `default_tx_group`、`service.default.grouplist=192.168.10.203:8091`、心跳及 RPC 参数。Seata 配置保持在 public，客户端注册方式仍为 file。应用需要能够连接协调器 8091 端口。

## 创建 Secret

清单引用当前应用命名空间中的两个 Secret：

- `app-runtime`：应用运行凭据。
- `ghcr-pull`：类型 `kubernetes.io/dockerconfigjson`，用于拉取私有 GHCR 镜像。账号 token 需要读取 packages 的权限。

`app-runtime` 必须包含下列键，所有值由部署时注入：

| Secret 键 | 用途 |
| --- | --- |
| `NACOS_USERNAME` / `NACOS_PASSWORD` | 业务命名空间及 public Seata 配置的读取权限 |
| `DB_USERNAME` / `DB_PASSWORD` | MySQL 账号与密码 |
| `REDIS_PASSWORD` | 和 `redis/redis-auth` 中的密码保持一致 |
| `SPRING_RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | 和 `rabbitmq/rabbitmq-auth` 中的账号、密码保持一致 |
| `JWT_SECRET` | 认证服务 HS512 签名密钥，至少 64 字节；所有认证副本使用相同值 |

Kubernetes Secret 不能跨命名空间直接引用。可从已有中间件 Secret 安全复制相关键至 `app-runtime`。不要把明文凭据、token 或包含这些值的文件提交到 Git；临时文件放在已被忽略的 `.local/` 下。Seata 控制台密码及服务器的 Secret Key 由已有协调器维护。

[prepare-application.py](prepare-application.py) 可准备命名空间和两个 Secret，并把当前 live Nacos 的八份业务配置复制、核对到 `luckyh-cloud-k8s`。它从 stdin JSON 读取 `NACOS_PASSWORD`、`GHCR_PULL_TOKEN`，可选 `NACOS_USERNAME`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET`，凭据值不打印。Redis、RabbitMQ 凭据从已有 Secret 读取；JWT 保留已有应用 Secret 中的值，否则生成强随机密钥。脚本运行环境需要 Python 3 和 PyYAML，并能访问 Nacos 和 Kubernetes。

本次实际使用 Windows 用户环境变量，经 SSH stdin 传入准备脚本，没有生成持久明文凭据文件。先将脚本复制到 master 的 `/tmp/luckyh-application/prepare-application.py`，在 PowerShell 7 中执行：

```powershell
$applicationInput = @{
    NACOS_USERNAME = [Environment]::GetEnvironmentVariable('NACOS_USERNAME', 'User')
    NACOS_PASSWORD = [Environment]::GetEnvironmentVariable('NACOS_PASSWORD', 'User')
    GHCR_PULL_TOKEN = [Environment]::GetEnvironmentVariable('GHCR_PULL_TOKEN', 'User')
}
if (!$applicationInput.NACOS_PASSWORD -or !$applicationInput.GHCR_PULL_TOKEN) {
    throw '请先设置 NACOS_PASSWORD 和 GHCR_PULL_TOKEN 用户环境变量。'
}
$applicationInput | ConvertTo-Json -Compress | ssh -T k8s-master 'python3 /tmp/luckyh-application/prepare-application.py'
Remove-Variable applicationInput
if ($LASTEXITCODE -ne 0) { throw '应用运行配置准备失败。' }
```

该准备操作会更新目标 Nacos 命名空间的八份配置，重复执行前应核对目标环境中需要保留的配置变更。

先创建命名空间和两个 Secret，再应用清单。下面是可选的手工创建方式，只有需要使用受限权限 env 文件时执行；本次部署未生成该凭据文件：

```bash
kubectl create namespace luckyh-cloud --dry-run=client -o yaml | kubectl apply -f -
kubectl -n luckyh-cloud create secret generic app-runtime \
  --from-env-file=.local/luckyh-k8s.env --dry-run=client -o yaml | kubectl apply --server-side --field-manager=luckyh-application -f -
```

## 应用与验证

在可访问集群且持有正确 kubeconfig 的机器，从仓库根目录执行：

```bash
kubectl apply --dry-run=server -f support/k8s/application.yml
sed 's/^  replicas: 1$/  replicas: 0/' support/k8s/application.yml | kubectl apply -f -
kubectl -n luckyh-cloud scale deployment/auth-service --replicas=1
kubectl -n luckyh-cloud rollout status deployment/auth-service --timeout=600s
```

以上先建立零副本 Deployment，再启动认证服务。确认该 Pod 就绪和节点可用内存后，用同样的 `scale`、`rollout status` 命令依次启动 `user-service`、`inventory-service`、`account-service`、`order-service`、`gateway-service`、`web`。七个应用都通过后，再按原清单应用一次以同步一副本声明，并检查状态：

```bash
kubectl apply -f support/k8s/application.yml
kubectl -n luckyh-cloud wait deployment --all --for=condition=Available --timeout=600s
kubectl -n luckyh-cloud get deployments,pods,services,ingress -o wide
```

Java 启动、存活、就绪探针使用 `/actuator/health/liveness` 和 `/actuator/health/readiness`，这些分组检查进程状态。数据库或 Redis 的短暂故障不会使存活探针重复重启整个应用。运行环境仅暴露 `health,info`，并关闭 health 细节显示。探针通过后仍需验证真实业务依赖与 Seata 事务。

前端 Nginx 在运行时读取 `GATEWAY_UPSTREAM=http://gateway-service:8080`，保留原始 `/api/...` 路径转发到网关，再由网关按 Nacos 服务名调用后端。Ingress 和前端 Nginx 的响应等待为 70 秒，网关订单路由仍使用已有的 60 秒配置。

首次部署逐个检查 Ready 和 RSS；资源不足时调整节点调度或容量后继续。检查 Pod 的 `OOMKilled`、Nacos 配置读取、Redis 认证、数据库连接及 Seata 注册日志。

从客户端检查完整入口：

```bash
curl -fsS http://192.168.10.200/
curl -fsS http://192.168.10.200/api/auth/health
curl -fsS --resolve cloud.home:80:192.168.10.200 http://cloud.home/api/auth/health
```

随后通过网页登录，验证用户、库存、账户、订单列表及退出登录。订单购买、支付、退款和回滚会修改业务数据，应使用明确的测试账号与商品，按 [事务验证步骤](../docs/distributed-transactions.md) 核对三库结果。

## 访问与更新

直接访问 [http://192.168.10.200/](http://192.168.10.200/) 即可。配置 Windows hosts 后可以使用 [http://cloud.home/](http://cloud.home/)：

```text
192.168.10.200 cloud.home
```

Ingress 同时配置 `cloud.home` 和无 host 的默认规则，两条规则都指向 web。已有 Redis、RabbitMQ、Nacos 等明确域名的 Ingress 继续按各自 Host 匹配。

更新镜像时在当前小内存集群中逐个 `set image`，等待对应 Deployment rollout 后再更新下一个；清单同步替换对应镜像标签，所有更新完成后重新应用。运行 ConfigMap 和 Secret 通过环境变量注入，修改后需要显式重启受影响的 Deployment；调整 Nacos 中的连接参数、日志初始化参数或 JWT 密钥后也要重启相应应用。

```bash
kubectl -n luckyh-cloud rollout restart deployment/auth-service
kubectl -n luckyh-cloud rollout status deployment/auth-service --timeout=600s
```

以上示例只重启认证服务。按应用逐个执行 restart 和 rollout status，观察可用内存后再继续下一个。

## 本次部署验收（2026-10-02）

- GitHub 后端 PR #3、前端 PR #3 已合并；对应 Actions 已成功构建并发布上述固定版本。集群实际从私有 GHCR 拉取七个镜像，使用 `ghcr-pull` Secret。
- 七个 Deployment 均为 `1/1 Available`，七个 Pod 均为 `1/1 Running`、重启次数 0；三个节点 Ready、无 MemoryPressure。六个 Java 应用的 `/actuator/health` 均为 `UP`。
- IP 入口 `http://192.168.10.200/`、`cloud.home` Host 路由及 `/api/auth/health` 均正常。浏览器使用 `user001` / `123456` 登录，用户、商品、订单、库存、余额和事务演示页面可访问；库存查询得到 997，用户 2 余额查询得到 10.00。订单页面刷新后仍显示真实列表。
- 认证令牌校验、登录用户列表、业务用户列表、商品列表、账户和订单接口均返回业务码 200；登录用户 3 条、业务用户 5 条、商品 8 条、订单 12 条。退出登录后，相同 Token 再访问订单接口返回 HTTP 401，Redis 黑名单生效。现有 admin 密码与公开演示密码不同，本次保留原值。
- 已执行一次 `POST /api/order/seata-demo/test-rollback`，输入 `userId=1, productId=1, quantity=1`。XID 为 `192.168.10.203:8091:5396141801273021783`，尝试订单 ID 15 查询返回业务码 404；用户 1 订单数 `7 → 7`，库存 `997 → 997`，余额 `4101.30 → 4101.30`。订单、库存、账户日志关联同一 XID，协调器记录 `Rollback global transaction successfully`，订单客户端记录 `Rollbacked`。
- `app-runtime` 与 `ghcr-pull` 均没有 `last-applied-configuration` annotation；准备脚本没有把凭据写入仓库或明文临时文件。Nacos 的八份应用配置已在独立 `luckyh-cloud-k8s` 命名空间发布并读回核对。

本次未执行正常购买、充值、退款等持久业务写入。回滚演示保留数据库自增编号间隙，业务数据已恢复到请求前的值。
