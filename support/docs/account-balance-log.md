# 余额明细

## 使用

账户管理选择登录用户后，点击“余额明细”查看充值、管理扣款、订单扣款和订单退款。明细按 ID 倒序分页，包含时间、变动类型、有符号金额、变动前后余额、订单号和事务号。历史余额不能推导出真实变动，不回填功能启用前的记录。

## 数据库准备

在现有 `luckyh_account` 库执行 [07-account-balance-log.sql](../sql/07-account-balance-log.sql)，然后更新账户、订单服务与前端。脚本只新增 `account_balance_log` 表，能够重复执行，不改已有余额。Go 的等价脚本是 `luckyh-cloud-go/deployments/sql/04-account-balance-log.sql`，共用账户库时只执行一份。

```powershell
mysql --no-defaults --default-character-set=utf8mb4 -h YOUR_DB_HOST -P 3306 -u YOUR_DB_USER -p --batch -e "source D:/project/demo/luckyh-cloud/support/sql/07-account-balance-log.sql"
if ($LASTEXITCODE -ne 0) { throw '余额明细建表失败，请先检查错误。' }
```

脚本未自动应用到现有业务数据库。未建表就使用新版余额写接口会失败，并由本地事务撤销余额变化。

## 查询接口

`GET /accounts/{userId}/balance-logs?current=1&size=10`，通过网关访问 `/api/account/accounts/{userId}/balance-logs`。`current` 至少为 1，`size` 为 1..100，非法参数返回 HTTP 400 / code 400。

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [{
      "id": 1,
      "userId": 1,
      "changeType": "DEBIT",
      "changeAmount": "-99.90",
      "beforeBalance": "10000.00",
      "afterBalance": "9900.10",
      "orderNo": "PURCHASE_example",
      "transactionId": "example-transaction",
      "createTime": "2026-10-04T12:00:00"
    }],
    "total": 1,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

三项金额为固定两位小数的字符串，避免浏览器浮点数舍入。管理操作的订单号与事务号为 `null`。无账户或没有明细返回空 `records`；查询不会创建账户。

## 事务与兼容

Java 在原 `@Transactional` 方法内写余额与明细，明细写入失败则余额也回滚。订单扣款/退款记录 `RootContext` 中的 XID，Seata AT 全局回滚一并撤销明细。AT 第一阶段提交后的数据可能短暂可见，不能把列表当作全局事务完成状态。

Go 的管理操作在原资源锁和本地事务内写入。订单 TCC Try 保存前后余额及订单号快照；Confirm 在同一事务内插入明细并更新分支状态、释放资源。重复 Confirm 不重复插入，插入失败保留可重试的 tried 状态；Cancel 恢复原余额且不写明细。旧分支快照没有新字段时不补造记录。Go 明细时间为 Confirm 写入时间。

两端继续使用原余额表、条件更新和金额上限。充值在原驱动 found-rows 设置下超过上限可能仍返回成功，但实际余额未变，此时不写零金额明细。Java/Go 事务号属于各自协调器，不能强行要求 XID 与 GID 一致。订单号新增为可选参数，原有调用仍可使用。

Java AT 与 Go TCC 不共用事务锁，订单、库存、账户写流量需按原迁移要求成组切换；共享表结构并不代表两套事务可以混合操作同一余额。

## 变更记录

- `Java与Go共用余额变动明细-20261004-1152-01`：新增共用表、分页协议和使用说明。
- 测试结果记录在 Go `docs/余额明细实现说明.md`；尚未执行业务服务部署或现有账户库升级。
