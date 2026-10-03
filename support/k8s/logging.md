# K8S 日志轮转与保留

本配置适用于当前 Seata 2.0.0、Nacos 3.2.3、RabbitMQ 4.3.6。更新镜像版本时，应重新对照镜像内的日志配置，保留其 logger 和 appender。

## 当前策略

| 对象 | 轮转与清理 | 存储 |
| --- | --- | --- |
| 应用主日志、Nginx、Ingress、Redis、Redis Proxy 的标准输出 | 三台 kubelet：单文件 10Mi，最多 5 个文件/容器 | 节点容器日志 |
| Seata ALL / WARN / ERROR | 单文件 50MB、7 天；每类归档上限 200MB，gzip | 容器内 `/root/logs/seata` |
| Nacos 34 类滚动文件日志 | 单文件 50MB、7 天；每个 appender 归档上限 200MB | 容器内 `/home/nacos/logs` |
| Nacos HTTP 访问日志 | 按天轮转，最多保留 7 天 | 同上 |
| RabbitMQ | 单文件 10MiB、5 份归档、gzip；同时输出到控制台 | 容器内 `/var/log/rabbitmq` 和节点 |
| RedisInsight | 镜像默认：单文件 20MB、7 天 | PVC `/data/logs` |

Seata、Nacos 的 `totalSizeCap` 针对归档，当前正在写入的文件另计；Nacos 的上限是每类日志的上限，并非整个 Pod 上限。RedisInsight 的 7 天保留没有总容量限制。Nacos GC 日志继续使用镜像原有的按大小/份数轮转。

这些是运行日志策略。Redis AOF/RDB、RabbitMQ 消息数据、Nacos 配置快照、数据库事务表和业务操作日志不能按本策略删除。外部 MySQL 的日志不由这些清单管理。

轮转负责控制本地空间，历史日志会被淘汰。`kubectl logs` 只读取最新日志文件；Seata、Nacos、RabbitMQ 的文件日志没有日志 PVC，容器重建可能丢失历史文件。当前未安装集中日志存储。

## 更新已有集群

使用 [apply-log-policy.py](apply-log-policy.py) 更新现有资源，只变更日志字段，保留 Rancher 中的其他配置、凭据、副本数和资源设置。需要服务器已有 Python 3、PyYAML、kubectl 和对应工作负载。不要用完整的 Seata/RabbitMQ 清单覆盖已调整过的运行配置。

从 Windows 仓库根目录复制文件：

```powershell
ssh k8s-master 'mkdir -p /tmp/luckyh-log-policy'
scp support/k8s/apply-log-policy.py support/k8s/seata.yml support/k8s/rabbitmq.yml support/k8s/nacos-logging.yml k8s-master:/tmp/luckyh-log-policy/
ssh k8s-master
```

先备份当前 ConfigMap、Pod 模板和容器日志到权限为 700 的目录，文件权限为 600。2026-10-02 本次部署的备份位于 master：

```text
/root/luckyh-backups/log-policy-20261002-155011
```

然后逐个执行，前一个恢复健康后再更新下一个：

```bash
python3 /tmp/luckyh-log-policy/apply-log-policy.py seata
kubectl -n seata rollout status deployment/seata-server --timeout=180s

python3 /tmp/luckyh-log-policy/apply-log-policy.py nacos
kubectl -n nacos rollout status statefulset/nacos --timeout=240s
```

Seata 当前单副本、Recreate 更新，会有短暂连接中断。Nacos 逐个更新；检查其健康和应用服务注册后继续。

RabbitMQ 使用 Khepri，需要保留仲裁。三副本集群先将更新分区设为 3，更新配置，再依次降到 2、1、0；每一步都等待新 Pod Ready，并确认三节点运行、Khepri 恢复一个 leader 和两个 follower、无告警，才继续：

```bash
kubectl -n rabbitmq patch statefulset rabbitmq --type=merge \
  -p '{"spec":{"updateStrategy":{"rollingUpdate":{"partition":3}}}}'
python3 /tmp/luckyh-log-policy/apply-log-policy.py rabbitmq

# 依次使用 2、1、0，每次恢复健康后再执行下一次。
kubectl -n rabbitmq patch statefulset rabbitmq --type=merge \
  -p '{"spec":{"updateStrategy":{"rollingUpdate":{"partition":2}}}}'
kubectl -n rabbitmq rollout status statefulset/rabbitmq --timeout=240s
kubectl -n rabbitmq exec rabbitmq-0 -- rabbitmq-diagnostics -q cluster_status
kubectl -n rabbitmq exec rabbitmq-0 -- rabbitmq-diagnostics -q metadata_store_status
kubectl -n rabbitmq exec rabbitmq-0 -- rabbitmq-diagnostics -q check_alarms
```

脚本使用配置摘要触发重建，配置不变时重复执行不会触发新滚动更新。Nacos 清单是已有 StatefulSet 的局部更新，不能单独用它首次部署 Nacos，也不要以 client-side apply 应用局部清单。

## 验证

检查 Seata、Nacos 挂载 XML 的 `maxFileSize`、`maxHistory`、`totalSizeCap`，并检查启动日志无 Logback 配置错误。Nacos 使用 `SERVER_TOMCAT_ACCESSLOG_MAXDAYS=7` 覆盖访问日志保留时间。

RabbitMQ 以实际 logger handler 为准：

```bash
kubectl -n rabbitmq exec rabbitmq-0 -- rabbitmqctl -q eval \
  '[maps:with([id,module,config], H) || H <- logger:get_handler_config()].'
```

文件 handler 应显示 `max_no_bytes=10485760`、`max_no_files=5`、`compress_on_rotate=true`；`rmq_1_stdout` 的 `type=standard_io` 表示控制台输出已启用。当前 RabbitMQ 4.3.6 的 `rabbitmqctl rotate_logs` 已弃用且不会轮转日志；文件达到 10MiB 后由 handler 自动轮转压缩，可检查 `/var/log/rabbitmq/*.gz`，无需手工删除日志。

## 本次运行验证（2026-10-03）

Nacos 三个 Pod 的 34 类文件日志和访问日志保留环境变量、Seata 的三类文件日志均已核对。RabbitMQ 三个节点的实际 handler 均为 10MiB / 5 份 / gzip，控制台输出启用；三节点无本地告警，Khepri 为一个 leader、两个 follower，提交索引一致。

与部署前备份比较，三个中间件的非日志工作负载设置未变化；六个应用的 `/actuator/health` 均为 `UP`。本次没有制造大量日志来触发大小轮转，因此未将压缩归档生成列为实测结果。外部 MySQL 主机的 SSH 当前无法认证，其文件日志策略尚未核实。

## 回滚

恢复对应备份中的 ConfigMap **内容和 Pod 模板**，再滚动更新并检查健康；RabbitMQ 同样逐节点恢复，最后将分区恢复为 0。不能只执行 `rollout undo`，因为它不会恢复 ConfigMap。

恢复 Pod 模板时用 JSON Patch `replace /spec/template` 替换为备份中的完整模板，确保移除本次添加的挂载、环境变量和摘要注解；JSON merge patch 会递归合并注解，无法删除新增注解。备份保留了当前完整设置，请勿打印其中可能包含的配置。

## 官方说明

- [K8S 容器日志轮转](https://kubernetes.io/zh-cn/docs/concepts/cluster-administration/logging/)
- [RabbitMQ 日志配置和轮转](https://www.rabbitmq.com/docs/logging)
- [Spring Boot 环境变量映射](https://docs.spring.io/spring-boot/reference/features/external-config.html)
