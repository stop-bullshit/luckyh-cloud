# GitHub Actions 与 GHCR 镜像发布

源码分别位于 `stop-bullshit/luckyh-cloud` 和 `stop-bullshit/luckyh-cloud-web`。GitHub Actions 构建并发布镜像到 `ghcr.io`，Kubernetes 节点直接拉取；无需另外部署镜像仓库。

## 发布入口和镜像

| 仓库 | 默认分支 | 发布内容 |
| --- | --- | --- |
| `luckyh-cloud` | `master` | 六个 Java 17 应用的独立镜像 |
| `luckyh-cloud-web` | `codex/luckyh-cloud-web` | Nginx 静态前端镜像 |

Pull Request 只执行检查与镜像构建，不推送 GHCR。默认分支更新、`v*` 标签和在默认分支手工运行 Actions 可发布镜像；发布前检查必须通过。工作流使用 GitHub 自动提供的 `GITHUB_TOKEN`，发布任务具有 `packages: write` 权限，无需把个人 Token 添加到构建工作流。

| 应用 | 镜像地址 | 应用端口 |
| --- | --- | --- |
| 网关 | `ghcr.io/stop-bullshit/luckyh-gateway-service` | 8080 |
| 用户 | `ghcr.io/stop-bullshit/luckyh-user-service` | 8081 |
| 订单 | `ghcr.io/stop-bullshit/luckyh-order-service` | 8082 |
| 认证 | `ghcr.io/stop-bullshit/luckyh-auth-service` | 8083 |
| 库存 | `ghcr.io/stop-bullshit/luckyh-inventory-service` | 8084 |
| 账户 | `ghcr.io/stop-bullshit/luckyh-account-service` | 8085 |
| 前端 | `ghcr.io/stop-bullshit/luckyh-cloud-web` | 80 |

每次发布包含 `sha-<完整提交 SHA>` 标签，Git 标签发布还包含对应的 `v*` 标签。部署使用明确的 SHA 标签或镜像 digest，例如：

```yaml
image: ghcr.io/stop-bullshit/luckyh-order-service:sha-<40位提交SHA>
```

回滚时恢复此前版本的镜像引用。更新仓库不会自动修改 Kubernetes Deployment，本次工作流仅发布镜像。

## GHCR 权限

新镜像默认私有，本次配置不改变镜像可见性。公开的源码仓库不代表镜像自动公开。在 GitHub 账号的 Packages 页面可以查看发布结果和权限。

K8S 拉取私有镜像使用 classic Personal Access Token，其权限为 `read:packages`，账号本身也必须具有镜像读取权限。构建工作流的临时 `GITHUB_TOKEN` 不用于集群长期拉取。

在已连接集群的 Bash 终端中创建拉取 Secret；交互输入 Token，不写入仓库：

```bash
kubectl create namespace luckyh-cloud --dry-run=client -o yaml | kubectl apply -f -
read -rsp 'GHCR read:packages Token: ' GHCR_PULL_TOKEN
printf '\n'
kubectl -n luckyh-cloud create secret docker-registry ghcr-pull \
  --docker-server=ghcr.io \
  --docker-username=stop-bullshit \
  --docker-password="$GHCR_PULL_TOKEN" \
  --dry-run=client -o yaml | kubectl apply -f -
unset GHCR_PULL_TOKEN
```

业务 Deployment 的 Pod 配置引用同一个 namespace 中的 Secret：

```yaml
spec:
  template:
    spec:
      imagePullSecrets:
        - name: ghcr-pull
```

所有运行应用的节点均需能够访问 GHCR，并完成实际镜像拉取验证；只测试 `https://ghcr.io/v2/` 返回认证要求只能证明接口连通。镜像公开与否由用户在 Packages 页面决定。

## 本地构建

后端从仓库根目录先运行 Maven，再选择需要的服务构建镜像。构建机需要 Docker；运行服务仍需要通过环境变量配置 Nacos、数据库、Redis和 JWT 等依赖。

```powershell
mvn -B -ntp verify
docker build --build-arg SERVICE=luckyh-order-service -t luckyh-order-service:local .
```

Docker 上下文只包含 Dockerfile 和六个服务的可执行 JAR。公共库已打包进入各服务的 Boot JAR；`support/` 下的部署资料、数据库脚本和私人环境文件不加入镜像。

运行时可通过 `JAVA_TOOL_OPTIONS` 调整 JVM 内存和参数。容器内存上限应为堆、元空间、线程栈及本地内存保留空间；应用镜像不预设 K8S 副本数或节点调度。

前端镜像构建方式与运行参数见配套仓库的 `deploy/ghcr.md`。前端生产代理通过 `GATEWAY_UPSTREAM` 设置网关，保留 `/api` 请求路径，例如：

```yaml
env:
  - name: GATEWAY_UPSTREAM
    value: http://luckyh-gateway-service:8080
```

上述 Service 名需与后续业务部署清单一致。Windows hosts 和本机用户环境变量不会自动进入 Pod。

## 发布与验收

1. 将发布配置合入对应默认分支，确认 Actions 的检查、镜像构建和推送均成功。
2. 在 Packages 页面确认七个镜像及对应 SHA 标签，并核对私有镜像读取权限。
3. 在应用 namespace 创建 `ghcr-pull`，使用明确版本部署并检查 `ImagePullBackOff` 等事件。
4. 注入 K8S 环境的 Nacos namespace、Service DNS 和业务凭据，等待 Pod Ready，再验证登录与真实订单事务。

参考：[GitHub 镜像发布](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images)、[GHCR 身份验证](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)、[Kubernetes 私有镜像拉取](https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/)。
