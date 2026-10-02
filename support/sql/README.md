# 数据库初始化

此目录只保留两份初始化脚本。应用不会自动执行它们。

| 脚本 | 数据库 | 用途 |
| --- | --- | --- |
| [01-business.sql](01-business.sql) | `luckyh_cloud` | 首次创建业务、认证、Seata AT 日志表及演示数据 |
| [02-seata.sql](02-seata.sql) | `seata` | 创建 Seata Server 2.0.0 的事务存储表 |

## 执行顺序

新环境先准备 MySQL、Redis 和 Nacos，再初始化业务库及 Seata 库，然后部署 Seata、发布 [Nacos 配置](../nacos/README.md)，最后启动应用。完整启动步骤见 [本地开发与启动](../docs/development.md)。当前环境已经初始化，无需重跑业务脚本。

安装 Oracle MySQL 8.x 命令行客户端后，在 PowerShell 执行以下命令。示例连接地址是 `192.168.10.13:3380`，账号按实际环境调整；`-p` 会交互询问密码，不把密码写进命令。

### 1. 首次创建业务库

```powershell
mysql --no-defaults --default-character-set=utf8mb4 -h 192.168.10.13 -P 3380 -u root -p --batch -e "source D:/project/demo/luckyh-cloud/support/sql/01-business.sql"
if ($LASTEXITCODE -ne 0) { throw '业务库初始化失败，请先检查错误，不要继续执行。' }
```

第一条 `CREATE DATABASE` 不带 `IF NOT EXISTS`，库已存在就停止。这个命令使用非交互执行模式，遇 SQL 错误停止；不要添加 `--force`，也不要改成在交互客户端中直接执行 `source`。Oracle MySQL 没有 `--abort-source-on-error` 选项。

库已存在时保留原数据，跳过此脚本。若初始化曾中途失败，先检查缺失的表和报错原因，再处理对应语句；不要删除整个库或继续执行全部种子数据。MySQL DDL 不会随后续失败整体回滚。

### 2. 初始化 Seata 库

业务库成功后执行：

```powershell
mysql --no-defaults --default-character-set=utf8mb4 -h 192.168.10.13 -P 3380 -u root -p --batch -e "source D:/project/demo/luckyh-cloud/support/sql/02-seata.sql"
if ($LASTEXITCODE -ne 0) { throw 'Seata 库初始化失败，请先检查错误。' }
```

这个脚本可再次执行：只创建缺失的库和表、补齐缺失的默认锁记录，不覆盖已有数据，也不更新已有表结构。表结构对应项目的 Seata `2.0.0`；切换 Seata 版本时，应核对该版本官方脚本。

## 表与服务

| 数据库 | 表 | 使用方 |
| --- | --- | --- |
| `luckyh_cloud` | `user` | 用户服务的业务用户 |
| `luckyh_cloud` | `order_info` | 订单服务 |
| `luckyh_cloud` | `sys_user` | 认证服务的登录账号，与业务用户分开 |
| `luckyh_cloud` | `sys_role`、`sys_permission` | 认证服务返回角色与权限 |
| `luckyh_cloud` | `sys_user_role`、`sys_role_permission` | 认证服务的账号、角色、权限关联 |
| `luckyh_cloud` | `undo_log` | 用户、订单服务的 Seata AT 回滚日志 |
| `seata` | `global_table`、`branch_table`、`lock_table`、`distributed_lock` | Seata Server 的事务与锁信息 |

当前用户、订单服务共用 `luckyh_cloud`，只需一张 `undo_log`。以后拆成不同业务库时，每个参与 AT 事务的库都需要该表。

## 演示数据

业务库首次初始化包含 5 个业务用户、5 个订单、3 个登录账号、2 个角色、10 项权限、3 条用户角色关联和 15 条角色权限关联。订单金额保留原示例数据，待支付、已支付状态均有示例。

| 登录账号 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `123456` | 管理员 |
| `user001` | `123456` | 普通用户 |
| `user002` | `123456` | 普通用户 |

密码以 BCrypt 保存；这里只使用公开演示密码。管理员角色 ID 为 `1`，普通用户角色 ID 为 `2`，认证服务注册逻辑使用这两个 ID。

## 初始化后检查

用数据库客户端执行以下只读语句。新库的计数应符合上面的演示数据；已经使用过的库会随业务操作变化。

```sql
SELECT table_schema, table_name
FROM information_schema.tables
WHERE table_schema IN ('luckyh_cloud', 'seata')
ORDER BY table_schema, table_name;

SELECT 'user' AS table_name, COUNT(*) AS row_count FROM luckyh_cloud.`user`
UNION ALL SELECT 'order_info', COUNT(*) FROM luckyh_cloud.order_info
UNION ALL SELECT 'sys_user', COUNT(*) FROM luckyh_cloud.sys_user
UNION ALL SELECT 'sys_role', COUNT(*) FROM luckyh_cloud.sys_role
UNION ALL SELECT 'sys_permission', COUNT(*) FROM luckyh_cloud.sys_permission
UNION ALL SELECT 'sys_user_role', COUNT(*) FROM luckyh_cloud.sys_user_role
UNION ALL SELECT 'sys_role_permission', COUNT(*) FROM luckyh_cloud.sys_role_permission;

SELECT id, role_code FROM luckyh_cloud.sys_role ORDER BY id;
SELECT lock_key FROM seata.distributed_lock ORDER BY lock_key;
```

业务库应有 8 张表，Seata 库应有 4 张表和 4 条默认锁记录。数据库连接参数放在 [Nacos 配置目录](../nacos/README.md)；初始化脚本不创建数据库账号，也不设置应用连接密码。

参考：[MySQL 客户端选项](https://dev.mysql.com/doc/refman/8.0/en/mysql-command-options.html)、[MySQL 8.0.44 客户端源码](https://github.com/mysql/mysql-server/blob/mysql-8.0.44/client/mysql.cc)、[Seata 2.0.0 服务端结构](https://github.com/apache/incubator-seata/blob/v2.0.0/script/server/db/mysql.sql)、[Seata 2.0.0 AT undo_log](https://github.com/apache/incubator-seata/blob/v2.0.0/script/client/at/db/mysql.sql)。
