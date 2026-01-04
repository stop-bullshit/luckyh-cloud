# Seata Server 数据库配置脚本

-- =====================================================
-- Seata Server 数据库表
-- 用于存储全局事务、分支事务和锁信息
-- =====================================================

-- 创建 seata 数据库
CREATE DATABASE IF NOT EXISTS `seata` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `seata`;

-- Global Table - 全局事务表
DROP TABLE IF EXISTS `global_table`;
CREATE TABLE `global_table` (
  `xid` varchar(128) NOT NULL COMMENT '全局事务ID',
  `transaction_id` bigint DEFAULT NULL COMMENT '事务ID',
  `status` tinyint NOT NULL COMMENT '状态：0-Begin, 1-Committing, 2-CommitRetrying, 3-Rollbacking, 4-RollbackRetrying, 5-TimeoutRollbacking, 6-TimeoutRollbackRetrying, 7-AsyncCommitting, 8-CommitFailed, 9-RollbackFailed, 10-Timeout, 11-Finished, 12-CommitRetryTimeout, 13-RollbackRetryTimeout, 14-Deleted',
  `application_id` varchar(32) DEFAULT NULL COMMENT '应用ID',
  `transaction_service_group` varchar(32) DEFAULT NULL COMMENT '事务分组',
  `transaction_name` varchar(128) DEFAULT NULL COMMENT '事务名称',
  `timeout` int DEFAULT NULL COMMENT '超时时间(ms)',
  `begin_time` bigint DEFAULT NULL COMMENT '开始时间',
  `application_data` varchar(2000) DEFAULT NULL COMMENT '应用数据',
  `gmt_create` datetime DEFAULT NULL COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`xid`),
  KEY `idx_status_gmt_modified` (`status`,`gmt_modified`),
  KEY `idx_transaction_id` (`transaction_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全局事务表';

-- Branch Table - 分支事务表
DROP TABLE IF EXISTS `branch_table`;
CREATE TABLE `branch_table` (
  `branch_id` bigint NOT NULL COMMENT '分支事务ID',
  `xid` varchar(128) NOT NULL COMMENT '全局事务ID',
  `transaction_id` bigint DEFAULT NULL COMMENT '事务ID',
  `resource_group_id` varchar(32) DEFAULT NULL COMMENT '资源组ID',
  `resource_id` varchar(256) DEFAULT NULL COMMENT '资源ID',
  `branch_type` varchar(8) DEFAULT NULL COMMENT '分支类型：AT, TCC, SAGA, XA',
  `status` tinyint DEFAULT NULL COMMENT '状态：0-Registered, 1-PhaseOneDone, 2-PhaseOneFailed, 3-PhaseOneTimeout, 4-PhaseTwoCommitted, 5-PhaseTwoCommitFailed_Retryable, 6-PhaseTwoCommitFailed_Unretryable, 7-PhaseTwoRollbacked, 8-PhaseTwoRollbackFailed_Retryable, 9-PhaseTwoRollbackFailed_Unretryable',
  `client_id` varchar(64) DEFAULT NULL COMMENT '客户端ID',
  `application_data` varchar(2000) DEFAULT NULL COMMENT '应用数据',
  `gmt_create` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `gmt_modified` datetime(6) DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`branch_id`),
  KEY `idx_xid` (`xid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分支事务表';

-- Lock Table - 全局锁表
DROP TABLE IF EXISTS `lock_table`;
CREATE TABLE `lock_table` (
  `row_key` varchar(128) NOT NULL COMMENT '行锁键',
  `xid` varchar(128) DEFAULT NULL COMMENT '全局事务ID',
  `transaction_id` bigint DEFAULT NULL COMMENT '事务ID',
  `branch_id` bigint NOT NULL COMMENT '分支事务ID',
  `resource_id` varchar(256) DEFAULT NULL COMMENT '资源ID',
  `table_name` varchar(32) DEFAULT NULL COMMENT '表名',
  `pk` varchar(36) DEFAULT NULL COMMENT '主键值',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0-locked, 1-rollbacking',
  `gmt_create` datetime DEFAULT NULL COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`row_key`),
  KEY `idx_branch_id` (`branch_id`),
  KEY `idx_xid` (`xid`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全局锁表';

-- Distributed Lock Table - 分布式锁表
DROP TABLE IF EXISTS `distributed_lock`;
CREATE TABLE `distributed_lock` (
  `lock_key` varchar(128) NOT NULL COMMENT '锁键',
  `lock_value` varchar(128) NOT NULL COMMENT '锁值',
  `expire` bigint DEFAULT NULL COMMENT '过期时间',
  PRIMARY KEY (`lock_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分布式锁表';

-- 插入默认分布式锁记录
INSERT INTO `distributed_lock` (`lock_key`, `lock_value`, `expire`) VALUES 
('AsyncCommitting', ' ', 0),
('RetryCommitting', ' ', 0),
('RetryRollbacking', ' ', 0),
('TxTimeoutCheck', ' ', 0);

-- 查看创建的表
SHOW TABLES;

-- 验证数据
SELECT 'global_table' as table_name, COUNT(*) as count FROM `global_table`
UNION ALL
SELECT 'branch_table' as table_name, COUNT(*) as count FROM `branch_table`
UNION ALL
SELECT 'lock_table' as table_name, COUNT(*) as count FROM `lock_table`
UNION ALL
SELECT 'distributed_lock' as table_name, COUNT(*) as count FROM `distributed_lock`;
