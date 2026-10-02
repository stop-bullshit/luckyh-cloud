# K8s：Seata 部署

六个后端应用和前端的集群清单、凭据准备与访问方式见 [应用部署](application.md)。

本文介绍当前 demo 的 Seata `2.0.0` 清单。Redis 与 RabbitMQ 集群的部署和访问方式见 [middleware.md](middleware.md)。应用启动脚本不执行数据库初始化，也不创建 Kubernetes 资源。

| 文件 | 用途 |
| --- | --- |
| [seata.yml](seata.yml) | Namespace、ConfigMap、单副本 Deployment、事务 Service、控制台 Service 和 Ingress |
| [../sql/02-seata.sql](../sql/02-seata.sql) | 独立 `seata` 数据库及四张事务表 |
| [../nacos/seataServer.properties](../nacos/seataServer.properties) | Nacos 下发给用户、库存、账户、订单服务的事务组和协调器地址 |

以下 Kubernetes 命令用于手工部署，需在已安装 `kubectl`、连接到目标集群的终端执行；应用清单的命令从仓库根目录执行。使用服务器上的 `kubectl` 时，可通过 `ssh k8s-master` 登录，并先把清单复制到对应执行目录。

## 当前清单参数

| 参数 | 当前值 | 换环境时检查 |
| --- | --- | --- |
| Namespace / Deployment | `seata` / `seata-server` | 命令与资源所属命名空间一致 |
| 镜像 | `docker.io/seataio/seata-server:2.0.0` | 初始化 SQL 必须匹配服务端版本 |
| 节点 | `k8s-node-01` | 按节点名称和可用资源调整 `nodeSelector` |
| 事务入口 | `192.168.10.203:8091` | LoadBalancer 地址、容器 `SEATA_IP`、Nacos `service.default.grouplist` 三处同步调整 |
| 控制台 | [http://seata.home/](http://seata.home/) | nginx Ingress 域名，后端 `seata-console:7091` |
| 控制台 DNS / hosts | `seata.home` → `192.168.10.200` | 与现有 `nacos.home` 使用相同 Ingress 入口 |
| 事务数据库 | `192.168.10.209:3306/seata` | ConfigMap 中 JDBC 地址，账号须能读写 Seata 表 |
| 资源请求 / 上限 | `100m / 500m` CPU、`256Mi / 768Mi` 内存 | JVM 堆为 `128m / 256m`，按节点余量调整 |

协调器使用 `registry.type=file`、`config.type=file`，服务端配置来自 ConfigMap。用户、库存、账户、订单服务使用 `registry.type=file`、`config.type=nacos`，读取 Nacos 的固定协调器地址。协调器自身不会出现在 Nacos 服务列表；六个应用继续通过 Nacos 注册和发现。

Seata 2.0.0 镜像内置的旧 Nacos 客户端使用当前 Nacos 3 已移除的旧注册 HTTP API，此前验证返回 501，因此这里采用固定协调器地址。

## 首次部署

### 1. 初始化 Seata 数据库

按照 [数据库初始化说明](../sql/README.md) 执行 [02-seata.sql](../sql/02-seata.sql)。它创建独立的 `seata` 库、四张服务端表和默认锁记录，可再次执行而不覆盖现有数据。

`luckyh_cloud` 由 `support/sql/01-business.sql` 首次初始化，包含订单写入需要的 `undo_log`。已有业务库不要重新初始化。购买演示另执行 [03-distributed-demo.sql](../sql/03-distributed-demo.sql) 补齐 `luckyh_inventory`、`luckyh_account`，两个库各有自己的 `undo_log`；03 不重置已有余额或库存。

### 2. 首次创建 Secret

Deployment 引用 `seata` 命名空间下的 `seata-runtime-credentials`。已有同名 Secret 时保留现有凭据。

| Secret 键 | 用途 |
| --- | --- |
| `DB_USERNAME` | Seata 数据库账号 |
| `DB_PASSWORD` | Seata 数据库密码 |
| `SEATA_CONSOLE_PASSWORD` | 控制台用户 `seata` 的登录密码 |
| `SEATA_SECRET_KEY` | 控制台令牌签名密钥 |

新集群可以在 Bash 终端执行以下步骤，需要 Python 3。先准备命名空间：

```bash
kubectl create namespace seata --dry-run=client -o yaml | kubectl apply -f -
```

下面程序交互读取数据库密码，默认数据库账号为 `root`（可先设置环境变量 `DB_USERNAME`）。控制台密码和签名密钥随机生成，凭据只通过标准输入交给 `kubectl`，不写入仓库或命令参数，不打印密码。它只创建缺失的 Secret，已有 Secret 时直接退出。

```bash
python3 - <<'PY'
import getpass
import json
import os
import secrets
import subprocess

existing = subprocess.run(
    ["kubectl", "-n", "seata", "get", "secret", "seata-runtime-credentials",
     "--ignore-not-found", "-o", "name"],
    check=True, capture_output=True, text=True,
)
if existing.stdout.strip():
    raise SystemExit("Secret 已存在，保留原凭据。")

db_password = os.environ.get("DB_PASSWORD") or getpass.getpass("Seata 数据库密码: ")
if not db_password:
    raise SystemExit("数据库密码不能为空。")

secret = {
    "apiVersion": "v1",
    "kind": "Secret",
    "metadata": {"name": "seata-runtime-credentials", "namespace": "seata"},
    "type": "Opaque",
    "stringData": {
        "DB_USERNAME": os.environ.get("DB_USERNAME", "root"),
        "DB_PASSWORD": db_password,
        "SEATA_CONSOLE_PASSWORD": secrets.token_urlsafe(24),
        "SEATA_SECRET_KEY": secrets.token_hex(32),
    },
}
subprocess.run(
    ["kubectl", "create", "-f", "-"],
    input=json.dumps(secret), text=True, check=True,
)
PY
```

### 3. 应用清单

确认上表的地址、节点和数据库参数后，从仓库根目录执行：

```bash
kubectl apply -f support/k8s/seata.yml
kubectl -n seata rollout status deployment/seata-server --timeout=180s
kubectl -n seata get pods,services,ingress
```

集群需要提供 LoadBalancer 地址分配能力和名为 `nginx` 的 IngressClass。控制台经 Ingress 访问，`8091` 是应用事务协议端口，应用客户端直接连接 LoadBalancer。

### 4. 准备客户端 Nacos 配置

在 Nacos **public（空命名空间 ID）**、**SEATA_GROUP** 下创建 Data ID **seataServer.properties**，内容取自 [../nacos/seataServer.properties](../nacos/seataServer.properties)。

```properties
service.vgroupMapping.default_tx_group=default
service.default.grouplist=192.168.10.203:8091
```

完成八份业务 YAML 和一份 public Seata 配置后，按 [本地开发说明](../docs/development.md) 启动六个应用。Nacos 分组和地址说明见 [Nacos 配置](../nacos/README.md)。

## 控制台登录

域名解析准备好后访问 [http://seata.home/](http://seata.home/)，用户名为 `seata`，密码保存在 Secret 中。在本机 Windows PowerShell 使用已有的 SSH 入口复制密码到剪贴板，不把明文打印到终端：

```powershell
$encodedPassword = ssh k8s-master "kubectl -n seata get secret seata-runtime-credentials -o jsonpath='{.data.SEATA_CONSOLE_PASSWORD}'"
if ($LASTEXITCODE -ne 0) { throw '未能读取 Seata 控制台凭据。' }
[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($encodedPassword)) | Set-Clipboard
Remove-Variable encodedPassword
```

本机已配置 `kubectl` 连接目标集群时，可把第一行替换为直接执行对应的 `kubectl -n seata get secret` 命令，后续检查、解码及剪贴板步骤相同。

## 修改配置后生效

服务端配置通过 ConfigMap 的 `subPath` 挂载；更新 ConfigMap 不会自动更新正在运行的进程。修改清单并应用后，显式重启 Deployment：

```bash
kubectl apply -f support/k8s/seata.yml
kubectl -n seata rollout restart deployment/seata-server
kubectl -n seata rollout status deployment/seata-server --timeout=180s
```

Secret 通过环境变量注入，凭据调整后也需要重启。当前采用单副本和 `Recreate` 策略，重启期间协调器短暂不可用，应避开订单写入操作。

### 当前 demo 的长回滚兼容配置

TC 使用 file provider，清单设置 `seata.transport.heartbeat=false`，用于当前 Seata 2.0.0 环境的长回滚兼容。用户、库存、账户、订单四个客户端使用 Nacos provider，传输参数由 public / `SEATA_GROUP` / `seataServer.properties` 提供：`transport.heartbeat=false`、`transport.rpcTmRequestTimeout=30000`、`transport.rpcRmRequestTimeout=15000`，不带 `seata.` 前缀，后两项单位毫秒；本地 Spring YAML 心跳项已删除。

TC 清单更新后按上面的步骤重启并等待 rollout；四个客户端在 public 配置发布后也必须全部重启。用 JVM attach 或等效方式核对实际心跳为 `false`、读 / 写空闲 `0 / 0` 和客户端 RPC 超时。只看 Nacos 配置、依赖刷新或只改单端不能确认这些静态值已生效。

TM 30 秒、RM 15 秒的有限 RPC 等待仍保留。网关仅将订单路由响应等待改为 `60000` 毫秒，其他路由仍为 `30s`；RPC 与协调器重试不能保证总时长不超过 60 秒。修复网络 / 存储延迟后，TC YAML 的 `seata.transport.heartbeat` 与客户端 public 配置的 `transport.heartbeat` 同步恢复为 `true`，全部重启，再验证五个场景。原因及依据见 [事务指南](../docs/distributed-transactions.md#当前-demo-的长回滚兼容处理)。

2026-10-02 14:38–14:40 的五个真实场景已完成三库数据核对。TC Ready，TC 与四个客户端的 JVM 心跳 / 读空闲 / 写空闲均已核对为 `false / 0 / 0`，客户端 TM / RM RPC 为 `30000ms / 15000ms`；账户恢复后也重新核对了这些参数。约 14:41 六应用健康均 `UP`、Nacos 六服务健康注册均通过。关闭心跳仍限于当前 demo 的兼容处理，详情见 [实测记录](../docs/distributed-transactions.md#8-本次实测记录)。

## 初始化后检查

1. Deployment 就绪；Pod 日志没有数据库认证或表不存在错误。
2. 控制台域名能访问并登录；事务入口 `192.168.10.203:8091` 从应用机器可达。
3. 用户、库存、账户、订单服务能读取 public / `SEATA_GROUP` 配置，TM/RM 能连接协调器。
4. 按 [跨服务事务测试](../docs/distributed-transactions.md) 验证三库购买提交和全写后回滚，同时核对订单、库存、余额及协调器最终状态；控制台可登录或服务健康不等于事务已验证。

更多问题见 [故障排查](../docs/troubleshooting.md)。
