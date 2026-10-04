# 数据库初始化

此目录保留初始化及升级脚本。应用不会自动执行它们。

| 脚本 | 数据库 | 用途 |
| --- | --- | --- |
| [01-business.sql](01-business.sql) | `luckyh_cloud` | 首次创建业务、认证、Seata AT 日志表及演示数据 |
| [02-seata.sql](02-seata.sql) | `seata` | 创建 Seata Server 2.0.0 的事务存储表 |
| [03-distributed-demo.sql](03-distributed-demo.sql) | `luckyh_inventory`、`luckyh_account` | 创建跨服务事务演示的库存、余额与 AT 日志表，补齐种子数据 |
| [04-product-management.sql](04-product-management.sql) | `luckyh_inventory` | 已有库存表升级商品编号自增，保留现有数据 |
| [05-order-product-id.sql](05-order-product-id.sql) | `luckyh_cloud` | 已有订单表增加商品 ID，供普通订单支付调用库存服务 |
| [06-order-lifecycle.sql](06-order-lifecycle.sql) | `luckyh_cloud` | 增加支付、取消、退款时间和订单操作流水 |
| [07-account-balance-log.sql](07-account-balance-log.sql) | `luckyh_account` | 新增余额明细，保留已有余额，不回填历史 |

## 执行顺序

新环境先准备 MySQL、Redis 和 Nacos，再按 `01`、`02`、`03` 顺序初始化各库，然后部署 Seata、发布 [Nacos 配置](../nacos/README.md)，最后启动应用。完整启动步骤见 [本地开发与启动](../docs/development.md)。已有 `luckyh_cloud` 库跳过 `01`；`02`、`03` 可执行以补齐缺失内容，不覆盖已有数据。

安装 Oracle MySQL 8.x 命令行客户端后，在 PowerShell 执行以下命令。示例连接地址是 `192.168.10.209:3306`，账号按实际环境调整；`-p` 会交互询问密码，不把密码写进命令。

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

### 3. 初始化库存与余额库

新环境和已有环境都需要执行 `03`，为库存、账户服务准备独立业务库：

```powershell
mysql --no-defaults --default-character-set=utf8mb4 -h 192.168.10.13 -P 3380 -u root -p --batch -e "source D:/project/demo/luckyh-cloud/support/sql/03-distributed-demo.sql"
if ($LASTEXITCODE -ne 0) { throw '库存与余额库初始化失败，请先检查错误。' }
```

所有表使用 `CREATE TABLE IF NOT EXISTS`，种子使用 `INSERT IGNORE`。重跑只补齐缺失的记录，保留已有商品信息、库存和余额，不更新已有表结构。

### 4. 已有库存库启用商品管理

新建库存库执行新版 `03` 后，商品编号已经自增。已有库存表需在没有进行中购买事务时执行 `04`，随后更新并重启库存服务：

```powershell
mysql --no-defaults --default-character-set=utf8mb4 -h 192.168.10.13 -P 3380 -u root -p --batch -e "source D:/project/demo/luckyh-cloud/support/sql/04-product-management.sql"
if ($LASTEXITCODE -ne 0) { throw '商品编号升级失败，请先检查错误。' }
```

升级仅为主键增加 `AUTO_INCREMENT`，保留已有商品编号与库存。之后在前端“商品管理”新增商品、编辑名称价格和补货，购买页可按名称选择；详见 [商品管理](../docs/product-management.md)。

### 5. 已有订单库增加商品关联

已有 `luckyh_cloud.order_info` 表执行 `05`，然后重启订单服务：

```powershell
mysql --no-defaults --default-character-set=utf8mb4 -h 192.168.10.13 -P 3380 -u root -p --batch -e "source D:/project/demo/luckyh-cloud/support/sql/05-order-product-id.sql"
if ($LASTEXITCODE -ne 0) { throw '订单商品字段升级失败，请先检查错误。' }
```

脚本只执行一次。新建订单会保存商品 ID；历史订单没有可靠的商品对应关系，字段保持为空，并在支付时提示无法扣减库存和余额。

### 6. 启用完整订单生命周期

执行 `05` 后继续执行 `06`，增加生命周期时间与操作流水表：

```powershell
mysql --no-defaults --default-character-set=utf8mb4 -h 192.168.10.13 -P 3380 -u root -p --batch -e "source D:/project/demo/luckyh-cloud/support/sql/06-order-lifecycle.sql"
if ($LASTEXITCODE -ne 0) { throw '订单生命周期升级失败，请先检查错误。' }
```

脚本只执行一次。升级后订单支持待支付、已支付、已取消、已退款四个状态，并记录支付、取消、退款时间以及每次状态变化的 XID。

### 7. 启用余额明细

账户库已有 `account_balance` 后执行 `07`，随后更新账户、订单服务与前端。Java、Go 使用相同的明细表，共用库只执行一份脚本；详情见 [余额明细](../docs/account-balance-log.md)。

```powershell
mysql --no-defaults --default-character-set=utf8mb4 -h YOUR_DB_HOST -P 3306 -u YOUR_DB_USER -p --batch -e "source D:/project/demo/luckyh-cloud/support/sql/07-account-balance-log.sql"
if ($LASTEXITCODE -ne 0) { throw '余额明细建表失败，请先检查错误。' }
```

脚本可以重复执行，不改原表、不回填历史记录。新版写接口依赖该表；建表失败时先处理错误，再启动新版应用。

## 表与服务

| 数据库 | 表 | 使用方 |
| --- | --- | --- |
| `luckyh_cloud` | `user` | 用户服务的业务用户 |
| `luckyh_cloud` | `order_info`、`order_operation_log` | 订单服务及状态操作流水 |
| `luckyh_cloud` | `sys_user` | 认证服务的登录账号，与业务用户分开 |
| `luckyh_cloud` | `sys_role`、`sys_permission` | 认证服务返回角色与权限 |
| `luckyh_cloud` | `sys_user_role`、`sys_role_permission` | 认证服务的账号、角色、权限关联 |
| `luckyh_cloud` | `undo_log` | 用户、订单服务的 Seata AT 回滚日志 |
| `luckyh_inventory` | `inventory`、`undo_log` | 库存服务及其 Seata AT 回滚日志 |
| `luckyh_account` | `account_balance`、`account_balance_log`、`undo_log` | 账户余额、余额明细及 Seata AT 回滚日志 |
| `seata` | `global_table`、`branch_table`、`lock_table`、`distributed_lock` | Seata Server 的事务与锁信息 |

用户、订单服务共用 `luckyh_cloud`；库存、账户分别使用 `luckyh_inventory`、`luckyh_account`。这三个参与 AT 事务的业务库各有一张 `undo_log`，Seata 协调器的 `seata` 库使用服务端事务表。

## 演示数据

业务库首次初始化包含 5 个业务用户、5 个订单、3 个登录账号、2 个角色、10 项权限、3 条用户角色关联和 15 条角色权限关联。订单金额保留原示例数据，待支付、已支付状态均有示例；新订单操作会写入订单操作流水。

| 登录账号 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `123456` | 管理员 |
| `user001` | `123456` | 普通用户 |
| `user002` | `123456` | 普通用户 |

密码以 BCrypt 保存；这里只使用公开演示密码。管理员角色 ID 为 `1`，普通用户角色 ID 为 `2`，认证服务注册逻辑使用这两个 ID。

`03` 首次执行提供以下跨服务事务数据：

| 商品ID | 商品 | 单价 | 可用库存 |
| --- | --- | ---: | ---: |
| 1 | Seata演示商品 | 99.90 | 1000 |
| 2 | 余额不足演示商品 | 500.00 | 1000 |
| 3 | 库存不足演示商品 | 9.90 | 0 |

账户对应业务用户 `luckyh_cloud.user.id`，与登录账号 ID 分开：用户 `1`、`3`、`4`、`5` 初始余额 `10000.00`，用户 `2` 初始余额 `10.00`。商品 `3` 用于库存不足场景；用户 `2` 购买商品 `2` 用于余额不足场景。已经执行过业务后，库存与余额会变化，重跑 `03` 不会恢复初值。

## 初始化后检查

用数据库客户端执行以下只读语句。新库的计数应符合上面的演示数据；已经使用过的库会随业务操作变化。

```sql
SELECT table_schema, table_name
FROM information_schema.tables
WHERE table_schema IN ('luckyh_cloud', 'luckyh_inventory', 'luckyh_account', 'seata')
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

SELECT product_id, product_name, product_price, available_quantity, update_time
FROM luckyh_inventory.inventory
ORDER BY product_id;

SELECT user_id, balance, update_time
FROM luckyh_account.account_balance
ORDER BY user_id;
```

`luckyh_cloud` 应有 9 张表，库存库有 2 张表，执行 `07` 后账户库有 3 张表，Seata 库有 4 张表和 4 条默认锁记录。`03` 首次初始化提供 3 个商品、5 个账户。数据库连接参数放在 [Nacos 配置目录](../nacos/README.md)；初始化脚本不创建数据库账号，也不设置应用连接密码。

参考：[MySQL 客户端选项](https://dev.mysql.com/doc/refman/8.0/en/mysql-command-options.html)、[MySQL 8.0.44 客户端源码](https://github.com/mysql/mysql-server/blob/mysql-8.0.44/client/mysql.cc)、[Seata 2.0.0 服务端结构](https://github.com/apache/incubator-seata/blob/v2.0.0/script/server/db/mysql.sql)、[Seata 2.0.0 AT undo_log](https://github.com/apache/incubator-seata/blob/v2.0.0/script/client/at/db/mysql.sql)。
