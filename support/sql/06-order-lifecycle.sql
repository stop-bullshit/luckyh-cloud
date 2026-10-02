-- 逻辑变动: 订单生命周期和操作流水持久化-20261002-1730-03
-- 已执行 05 的存量业务库升级：增加生命周期时间与订单操作流水。
USE `luckyh_cloud`;

ALTER TABLE `order_info`
  MODIFY COLUMN `status` tinyint NOT NULL DEFAULT '0' COMMENT '订单状态：0-待支付，1-已支付，2-已取消，3-已退款',
  ADD COLUMN `pay_time` datetime DEFAULT NULL COMMENT '支付时间' AFTER `status`,
  ADD COLUMN `cancel_time` datetime DEFAULT NULL COMMENT '取消时间' AFTER `pay_time`,
  ADD COLUMN `refund_time` datetime DEFAULT NULL COMMENT '退款时间' AFTER `cancel_time`;

CREATE TABLE `order_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(50) NOT NULL COMMENT '订单号',
  `operation_type` varchar(20) NOT NULL COMMENT '操作类型',
  `from_status` tinyint DEFAULT NULL COMMENT '操作前状态',
  `to_status` tinyint NOT NULL COMMENT '操作后状态',
  `xid` varchar(128) DEFAULT NULL COMMENT 'Seata全局事务ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单操作流水';
