-- 跨服务事务演示：库存与余额使用独立业务库，各自保留 Seata AT 日志。
-- 可重复执行，只创建缺失表、插入缺失种子，不重置已有库存或余额。

CREATE DATABASE IF NOT EXISTS `luckyh_inventory`
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE `luckyh_inventory`;

CREATE TABLE IF NOT EXISTS `inventory` (
  `product_id` bigint NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `product_name` varchar(128) NOT NULL COMMENT '商品名称',
  `product_price` decimal(18,2) NOT NULL COMMENT '商品单价',
  `available_quantity` int NOT NULL COMMENT '可用库存数量',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品库存';

CREATE TABLE IF NOT EXISTS `undo_log` (
  `branch_id` bigint NOT NULL COMMENT 'branch transaction id',
  `xid` varchar(128) NOT NULL COMMENT 'global transaction id',
  `context` varchar(128) NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` longblob NOT NULL COMMENT 'rollback info',
  `log_status` int NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
  `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`),
  KEY `ix_log_created` (`log_created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Seata AT模式undo日志表';

INSERT IGNORE INTO `inventory` (`product_id`, `product_name`, `product_price`, `available_quantity`) VALUES
(1, 'Seata演示商品', 99.90, 1000),
(2, '余额不足演示商品', 500.00, 1000),
(3, '库存不足演示商品', 9.90, 0);

CREATE DATABASE IF NOT EXISTS `luckyh_account`
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE `luckyh_account`;

CREATE TABLE IF NOT EXISTS `account_balance` (
  `user_id` bigint NOT NULL COMMENT '业务用户ID',
  `balance` decimal(18,2) NOT NULL COMMENT '可用余额',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账户余额';

CREATE TABLE IF NOT EXISTS `undo_log` (
  `branch_id` bigint NOT NULL COMMENT 'branch transaction id',
  `xid` varchar(128) NOT NULL COMMENT 'global transaction id',
  `context` varchar(128) NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` longblob NOT NULL COMMENT 'rollback info',
  `log_status` int NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
  `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`),
  KEY `ix_log_created` (`log_created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Seata AT模式undo日志表';

INSERT IGNORE INTO `account_balance` (`user_id`, `balance`) VALUES
(1, 10000.00),
(2, 10.00),
(3, 10000.00),
(4, 10000.00),
(5, 10000.00);
