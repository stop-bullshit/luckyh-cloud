-- 逻辑变动: Java与Go共用余额变动明细-20261004-1152-01
-- 在已有账户库上执行；只新增明细表，不修改余额，不回填历史流水。
-- Java / Go 指向同一账户库时只需执行其中一份等价脚本。
USE `luckyh_account`;

CREATE TABLE IF NOT EXISTS `account_balance_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `user_id` bigint NOT NULL COMMENT '登录用户ID',
  `change_type` varchar(16) NOT NULL COMMENT 'RECHARGE充值、DEDUCT管理扣款、DEBIT订单扣款、CREDIT订单退款',
  `change_amount` decimal(18,2) NOT NULL COMMENT '有符号变动金额，增加为正、扣减为负',
  `before_balance` decimal(18,2) NOT NULL COMMENT '变动前余额',
  `after_balance` decimal(18,2) NOT NULL COMMENT '变动后余额',
  `order_no` varchar(64) DEFAULT NULL COMMENT '关联订单号，管理操作为空',
  `transaction_id` varchar(128) DEFAULT NULL COMMENT 'Seata XID或DTM GID，管理操作为空',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '明细写入时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id_id` (`user_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账户余额变动明细';
