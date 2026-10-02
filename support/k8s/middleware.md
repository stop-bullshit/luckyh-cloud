# K8s：Redis 与 RabbitMQ 集群

本清单适用于当前 3 节点 Kubernetes 1.28 集群：`nginx` Ingress 对外地址为 `192.168.10.200`。Redis 是 3 主 3 从的 Redis Cluster，RabbitMQ 是 3 节点集群。Redis Insight 和 RabbitMQ 管理页面分别使用 `redis.home`、`rabbit.home`；本机应用通过下方独立的数据端口连接。

| 文件 | 用途 |
| --- | --- |
| [redis-cluster.yml](redis-cluster.yml) | 6 个 Redis Pod、内部节点 Service、PVC |
| [redis-proxy.yml](redis-proxy.yml) | Envoy Redis Cluster 代理、单端口 Redis API Service |
| [redis-cluster-external.yml](redis-cluster-external.yml) | 6 个 Redis 节点的外部 Service，供 Cluster 客户端直连 |
| [redis-insight.yml](redis-insight.yml) | Redis Insight、持久卷、带 Basic Auth 的 Ingress |
| [rabbitmq.yml](rabbitmq.yml) | 3 节点 RabbitMQ、PVC、管理页面 Ingress |
| [create-middleware-secrets.py](create-middleware-secrets.py) | 首次生成密码和 Erlang cookie，保留已有 Secret |
| [bootstrap-redis-cluster.py](bootstrap-redis-cluster.py) | 按节点分布创建 Redis 主从关系 |
| [verify-middleware.py](verify-middleware.py) | 从集群入口验证认证、Redis 槽位和 RabbitMQ 节点 |

## 首次部署

先在本机仓库根目录将文件复制到 `k8s-master`，后续命令在服务器上执行：

```powershell
ssh k8s-master "mkdir -p /tmp/luckyh-middleware"
scp support/k8s/redis-cluster.yml support/k8s/redis-proxy.yml support/k8s/redis-cluster-external.yml support/k8s/redis-insight.yml support/k8s/rabbitmq.yml support/k8s/create-middleware-secrets.py support/k8s/bootstrap-redis-cluster.py support/k8s/verify-middleware.py k8s-master:/tmp/luckyh-middleware/
ssh k8s-master
```

集群需已有 MetalLB，且地址池可分配 `192.168.10.202`、`192.168.10.204` 与 `192.168.10.205`。集群原本没有 StorageClass，先安装固定版本的 Rancher local-path-provisioner。它不会设置默认 StorageClass；本清单明确使用 `local-path`。

```bash
kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/v0.0.36/deploy/local-path-storage.yaml
kubectl rollout status deployment/local-path-provisioner -n local-path-storage --timeout=120s
```

Secret 仅保存在 Kubernetes 中，不写入仓库；脚本重跑时保留已有凭据。

```bash
python3 /tmp/luckyh-middleware/create-middleware-secrets.py
kubectl apply -f /tmp/luckyh-middleware/redis-cluster.yml
kubectl apply -f /tmp/luckyh-middleware/rabbitmq.yml
kubectl rollout status statefulset/redis-cluster -n redis --timeout=240s
kubectl rollout status statefulset/rabbitmq -n rabbitmq --timeout=240s
python3 /tmp/luckyh-middleware/bootstrap-redis-cluster.py
kubectl apply -f /tmp/luckyh-middleware/redis-proxy.yml
kubectl rollout status deployment/redis-proxy -n redis --timeout=240s
kubectl apply -f /tmp/luckyh-middleware/redis-cluster-external.yml
kubectl apply -f /tmp/luckyh-middleware/redis-insight.yml
kubectl rollout status deployment/redis-insight -n redis --timeout=240s
python3 /tmp/luckyh-middleware/verify-middleware.py
```

Redis 的每个 Pod 宣告对应的 `redis-node-0` 至 `redis-node-5` Service 域名及客户端端口 `6379` 至 `6384`。六个外部 Service 共享 `192.168.10.205`，分别转发上述端口；节点通信端口 `16379` 只留在集群内。普通 Redis 客户端仍通过 Envoy 的 `redis-api.home:6379`（`192.168.10.202`）单端口访问。引导脚本要求 3 个节点各运行 2 个 Redis Pod，并将每个从节点放在其主节点之外。再次执行脚本不会重建已有集群。

## 本机访问

在运行本机应用的 Windows `hosts` 中配置管理页面和数据入口域名。使用 Redis Cluster 客户端直连时，再增加六个节点域名映射；普通客户端走代理时不需要这六行：

```text
192.168.10.200 redis.home rabbit.home
192.168.10.202 redis-api.home
192.168.10.204 rabbit-api.home
192.168.10.205 redis-node-0.redis.svc.cluster.local
192.168.10.205 redis-node-1.redis.svc.cluster.local
192.168.10.205 redis-node-2.redis.svc.cluster.local
192.168.10.205 redis-node-3.redis.svc.cluster.local
192.168.10.205 redis-node-4.redis.svc.cluster.local
192.168.10.205 redis-node-5.redis.svc.cluster.local
```

- [http://redis.home/](http://redis.home/)：浏览器 Basic Auth 用户名 `admin`，密码为 `redis/redis-auth` Secret 的 `insight-basic-password`；进入页面后已有名为 `Kubernetes Redis Cluster` 的连接。
- [http://rabbit.home/](http://rabbit.home/)：用户名 `admin`，密码为 `rabbitmq/rabbitmq-auth` Secret 的 `password`。

本机服务和客户端的数据连接如下；`redis.home` 与 `rabbit.home` 只用于浏览器管理页面，不是数据协议入口。

| 服务 | 本机连接地址 | 认证 |
| --- | --- | --- |
| Redis 代理 API（当前默认） | `redis-api.home:6379`（`192.168.10.202`），普通 Redis 客户端、RESP2，不启用 Cluster 模式 | `redis/redis-auth` Secret 的 `password` |
| Redis Cluster 直连 | `192.168.10.205:6379–6384`，Cluster 客户端；节点域名与端口见下文 | 同一 Redis 密码 |
| RabbitMQ AMQP | `rabbit-api.home:5672`（`192.168.10.204`） | 用户名 `admin`，密码为 `rabbitmq/rabbitmq-auth` Secret 的 `password` |

本仓库的六个业务服务从 Nacos `luckyh-cloud / DEFAULT_GROUP / db-common.yml` 读取代理单地址，默认走代理并使用 RESP2；同一 Data ID 的 `luckyh.redis.cluster-nodes` 保存逗号分隔的六个 Cluster 节点入口，也提供 `spring.rabbitmq.host/port/username/password`。`support/scripts/start-services.ps1` 会将 Redis 和 RabbitMQ Secret 的密码提供给本机 Java 进程；直接从 IDEA 启动时需自行设置所用中间件的密码。当前业务服务尚未接入 MQ 模块。应用运行在其他机器时，也须让该机器能解析 `redis-api.home`、`rabbit-api.home`，或用 `SPRING_DATA_REDIS_HOST`、`SPRING_RABBITMQ_HOST` 覆盖对应地址。

要在本机改用 Redis Cluster 客户端，先完成上面的六个 `hosts` 映射，再让对应进程从 Nacos 读取 `luckyh.redis.cluster-nodes`。客户端发现槽位后仍会连接其他节点，因此六个域名和 `6379–6384` 端口必须全部可达。不要把代理 `redis-api.home:6379` 放入 `cluster.nodes`。

```powershell
$env:SPRING_DATA_REDIS_CLUSTER_NODES = '${luckyh.redis.cluster-nodes}'
```

启动或重启应用后，Spring Boot 检测到 `cluster.nodes` 并使用 Cluster 连接；其他进程继续使用 Nacos 中的代理 `host`、`port`。切回代理时执行 `Remove-Item Env:SPRING_DATA_REDIS_CLUSTER_NODES`，再重启应用。密码仍由 `REDIS_PASSWORD` 提供。客户端运行在哪台电脑，就在那台电脑配置六个节点域名解析。

在本机 PowerShell 将密码复制到剪贴板，不在终端打印明文：

```powershell
$encodedPassword = ssh k8s-master "kubectl -n redis get secret redis-auth -o jsonpath='{.data.insight-basic-password}'"
if ($LASTEXITCODE -ne 0) { throw '未能读取 Redis Insight 密码。' }
[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($encodedPassword)) | Set-Clipboard
Remove-Variable encodedPassword
```

RabbitMQ 密码将第一行改为 `ssh k8s-master "kubectl -n rabbitmq get secret rabbitmq-auth -o jsonpath='{.data.password}'"`。Redis 数据密码使用 `redis/redis-auth` 的 `password` 键，Redis Insight 已自动读取它。

## 集群内应用地址与存储

| 服务 | 集群内地址 | 说明 |
| --- | --- | --- |
| Redis API | `redis-api.redis.svc.cluster.local:6379` | 使用普通 Redis 客户端、RESP2 |
| RabbitMQ AMQP | `rabbitmq.rabbitmq.svc.cluster.local:5672` | 用户名、密码来自 `rabbitmq-auth` |

Nacos 的 `luckyh-cloud / DEFAULT_GROUP / db-common.yml` 已在 2026-10-02 同步 Redis 代理域名、Cluster 节点列表及 RabbitMQ AMQP 配置，远端回读与本地文件一致。本地认证服务加载新增属性后，默认代理模式的 Redis 与整体健康检查均为 `UP`；此前直连 Cluster 模式也已验证。RabbitMQ 管理 API 认证和本机 AMQP 端口连通均通过。RabbitMQ 集群共享元数据，业务消息需要使用 quorum queue 才有跨节点副本；普通 classic queue 不会自动复制。

`local-path` PVC 绑定节点本地磁盘，节点永久故障时不能自动迁移数据，需从副本和备份恢复。本部署按家用演示集群的内存余量设置了较小资源限额，两个管理入口使用局域网 HTTP。
