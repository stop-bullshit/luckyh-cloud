# 代码提交、自动发布与 K8S 部署

日常发布通过 GitHub Actions 和集群现有 Fleet 完成。前后端分别更新自己的仓库，集群主动拉取部署清单，GitHub Actions 不需要连接内网的 SSH 或 Kubernetes API。

## 发布入口

| 应用 | 仓库默认分支 | Actions | Fleet 监视目录 |
| --- | --- | --- | --- |
| 六个后端服务 | `stop-bullshit/luckyh-cloud` / `master` | `.github/workflows/publish-images.yml` | `support/gitops/backend` |
| 前端 | `stop-bullshit/luckyh-cloud-web` / `codex/luckyh-cloud-web` | `.github/workflows/publish-image.yml` | `deploy/k8s` |

```text
修改代码 → 提交开发分支 → PR 检查通过 → 合并到默认分支
                                      ↓
                             Actions 校验并构建
                                      ↓
                       发布 GHCR 的 sha-完整提交ID 镜像
                                      ↓
                    github-actions[bot] 更新本仓库部署清单
                                      ↓
                          Fleet 轮询 GitHub 并同步资源
                                      ↓
                         K8S 滚动更新 → 检查应用就绪
```

后端执行 Maven `verify`、六个镜像构建及容器权限检查，六个镜像全部发布成功后才更新部署版本。前端执行 lint、测试、镜像构建和 Nginx 配置检查，通过后发布镜像并更新前端版本。部署固定完整提交 ID 的 `sha-...` 镜像，不使用 `latest` 选择线上版本。

PR 只校验和构建镜像，不发布或部署。推送 `v*` 标签只发布镜像，不更新线上部署清单。默认分支 push 和默认分支的手动 Actions 执行可以发布并更新 GitOps 版本；仅修改部署目录的 push 不重复构建应用。

自动更新任务读取最新默认分支，遇到更新的代码提交时跳过旧镜像。构建期间有人手工修改部署镜像清单时，手工部署变更优先，在途旧构建也会跳过；机器人此前的自动部署提交仍允许后续自动更新。任务串行执行，推送冲突时重新读取分支，最多尝试三次，不强制覆盖远端提交。机器人更新部署清单使用 `GITHUB_TOKEN`，不会触发重复构建。

## 首次接管准备

Fleet 的两个 GitRepo 分别关联上表仓库、默认分支和目录，目标是当前集群。部署范围限定为 `luckyh-cloud` 命名空间：

- 后端目录管理六个 Deployment 和对应 Service。
- 前端目录管理 `web` Deployment、Service 和 `cloud` Ingress。
- `fleet.yaml` 的 `helm.takeOwnership: true` 用于接管已经存在的资源。
- Nacos、数据库、Redis、RabbitMQ、Seata 和应用运行 ConfigMap/Secret 继续使用已准备的资源。

接管时必须保留线上配置。当前 GitOps 后端清单以已有资源为基线，订单和库存各两个副本，其他后端各一个；镜像、资源限制、探针和调度规则也从已有资源保留。之后扩容或修改应用部署参数，应提交 GitOps 清单。

各仓库 Actions 分别使用 GitHub 自动提供的 `GITHUB_TOKEN`：镜像发布任务需要 `packages: write`，部署清单更新任务需要 `contents: write`。两者不跨仓库写入，因此无需新增 PAT。集群已有 `ghcr-pull` 继续负责拉取私有镜像；其长期拉取凭据与 Actions 的临时令牌用途不同。

`support/k8s/application.yml` 和此前手工复制到服务器的 `web.yaml` 用于首次初始化或部署参考。Fleet 接管后，不要重新应用这些旧副本，否则可能把线上镜像、实例数或配置覆盖回旧值。前端当前部署来源为前端仓库的 `deploy/k8s/web.yaml`。

## 日常发布与检查

1. 修改代码，提交并推送开发分支，PR 检查通过后合并到对应默认分支。
2. 在 GitHub Actions 确认校验、镜像发布和部署清单更新任务成功。
3. 查看默认分支机器人提交中的镜像 SHA；该 SHA 对应本次应用代码提交。
4. 在 Rancher/Fleet 查看对应 GitRepo、Bundle 的同步及 Ready 状态，再检查应用。

在集群终端可检查：

```bash
kubectl -n fleet-local get gitrepos,bundles
kubectl -n luckyh-cloud get deployments,pods
kubectl -n luckyh-cloud rollout status deployment/order-service --timeout=600s
kubectl -n luckyh-cloud rollout status deployment/web --timeout=600s
```

镜像拉取失败时先看 Pod 事件和 `ghcr-pull` 的读取权限；应用未就绪时检查启动日志、Nacos 和数据库连接。Actions 成功后 Fleet 尚未 Ready，表示集群部署仍需检查。最终验收应包含登录和关键接口；涉及事务改动时执行 [跨服务事务验证](../docs/distributed-transactions.md)。

## 回滚

从此前成功的部署提交中取出镜像 SHA，将对应 GitOps 清单里的镜像引用恢复后提交默认分支：

- 后端：修改 `support/gitops/backend/applications.yaml` 中需要回滚的服务镜像。
- 前端：修改前端仓库 `deploy/k8s/web.yaml` 中的前端镜像。

Fleet 会同步恢复的版本。仅修改部署目录不会重新发布应用镜像；原 SHA 镜像需要仍存在于 GHCR。联动修改多个服务时，回滚到经过一起验证的版本组合。数据库结构和 Nacos 配置不会随镜像回滚自动恢复，涉及它们的变更需单独准备兼容和恢复步骤。

手工回滚提交会阻止已经在运行的旧构建覆盖回滚版本。下一次提交新的应用代码并通过发布检查后，会按该新代码版本继续自动发布。

## 新增后端服务

新增服务时同步准备以下内容：

1. 根 Maven 模块、服务的可执行 Boot JAR，以及所需业务库或表。
2. 复用根目录参数化 `Dockerfile`，确认新模块名称能被 `.dockerignore` 纳入构建上下文；如果运行要求不同，再调整 Dockerfile。
3. 将模块加入 `publish-images.yml` 的服务矩阵和 `service-jars` 上传路径；更新 `update-gitops-images.py` 的服务名单与镜像匹配，使发布成功后包含新镜像。
4. 在 `support/gitops/backend/applications.yaml` 加入 Deployment 和 Service，配置端口、探针、资源、副本及已有运行 ConfigMap/Secret 引用。
5. 准备 K8S 使用的 Nacos 配置、数据库权限、服务注册名称和必要的网关路由；涉及 Seata 时准备事务配置及 `undo_log`。
6. PR 校验通过后合并，确认 GHCR 镜像、Fleet Ready、服务注册和业务调用。

Actions 和 Fleet 不会自动执行数据库初始化，也不会将仓库里的 Nacos 配置自动发布到 Nacos。新增服务必须先补齐这些运行依赖，再发布代码。

参考：[Fleet 清单与资源接管](https://fleet.rancher.io/reference/ref-fleet-yaml)、[GitHub 自动令牌权限和触发行为](https://docs.github.com/en/actions/concepts/security/github_token)。
