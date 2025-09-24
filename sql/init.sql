-- 创建数据库
CREATE DATABASE IF NOT EXISTS `luckyh_cloud`
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE `luckyh_cloud`;

-- 创建用户表
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `real_name` varchar(50) NOT NULL COMMENT '真实姓名',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 插入用户测试数据
INSERT INTO `user` (`username`, `real_name`, `email`, `phone`, `status`) VALUES
('zhangsan', '张三', 'zhangsan@example.com', '13800138001', 1),
('lisi', '李四', 'lisi@example.com', '13800138002', 1),
('wangwu', '王五', 'wangwu@example.com', '13800138003', 1),
('zhaoliu', '赵六', 'zhaoliu@example.com', '13800138004', 1),
('qianqi', '钱七', 'qianqi@example.com', '13800138005', 1);

-- 创建订单表
DROP TABLE IF EXISTS `order_info`;
CREATE TABLE `order_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no` varchar(50) NOT NULL COMMENT '订单号',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `product_name` varchar(200) NOT NULL COMMENT '商品名称',
  `product_price` decimal(10,2) NOT NULL COMMENT '商品价格',
  `quantity` int NOT NULL COMMENT '购买数量',
  `total_amount` decimal(10,2) NOT NULL COMMENT '订单总金额',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '订单状态：0-待支付，1-已支付，2-已取消',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- 插入订单测试数据
INSERT INTO `order_info` (`order_no`, `user_id`, `product_name`, `product_price`, `quantity`, `total_amount`, `status`) VALUES
('ORDER_2024091900001', 1, 'iPhone 15 Pro', 8999.00, 1, 8999.00, 1),
('ORDER_2024091900002', 2, 'MacBook Pro', 15999.00, 1, 15999.00, 1),
('ORDER_2024091900003', 1, 'AirPods Pro', 1999.00, 2, 3998.00, 0),
('ORDER_2024091900004', 3, 'iPad Air', 4599.00, 1, 4599.00, 1),
('ORDER_2024091900005', 4, 'Apple Watch', 2999.00, 1, 2999.00, 0);

-- 查看表结构和数据
SHOW TABLES;
SELECT COUNT(*) as user_count FROM `user`;
SELECT COUNT(*) as order_count FROM `order_info`;

-- 验证数据
SELECT u.username, u.real_name, COUNT(o.id) as order_count
FROM `user` u
LEFT JOIN `order_info` o ON u.id = o.user_id
GROUP BY u.id, u.username, u.real_name
ORDER BY u.id;

-- ============================================
-- 认证授权相关表
-- ============================================

-- 创建系统用户表
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '密码',
  `real_name` varchar(50) NOT NULL COMMENT '真实姓名',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `avatar` varchar(200) DEFAULT NULL COMMENT '头像',
  `user_type` tinyint NOT NULL DEFAULT '2' COMMENT '用户类型：1-管理员，2-普通用户',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` varchar(50) DEFAULT NULL COMMENT '最后登录IP',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_email` (`email`),
  KEY `idx_status` (`status`),
  KEY `idx_user_type` (`user_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- 创建角色表
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` varchar(50) NOT NULL COMMENT '角色名称',
  `role_code` varchar(50) NOT NULL COMMENT '角色编码',
  `description` varchar(200) DEFAULT NULL COMMENT '角色描述',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 创建权限表
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `permission_name` varchar(50) NOT NULL COMMENT '权限名称',
  `permission_code` varchar(100) NOT NULL COMMENT '权限编码',
  `resource_type` tinyint NOT NULL DEFAULT '1' COMMENT '资源类型：1-菜单，2-按钮，3-接口',
  `parent_id` bigint DEFAULT '0' COMMENT '父权限ID',
  `path` varchar(200) DEFAULT NULL COMMENT '路径',
  `component` varchar(200) DEFAULT NULL COMMENT '组件',
  `icon` varchar(50) DEFAULT NULL COMMENT '图标',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`permission_code`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- 创建用户角色关联表
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- 创建角色权限关联表
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- ============================================
-- 插入基础数据
-- ============================================

-- 插入角色数据
INSERT INTO `sys_role` (`role_name`, `role_code`, `description`) VALUES
('超级管理员', 'ADMIN', '系统超级管理员，拥有所有权限'),
('普通用户', 'USER', '普通用户，拥有基础权限');

-- 插入权限数据
INSERT INTO `sys_permission` (`permission_name`, `permission_code`, `resource_type`, `parent_id`) VALUES
('用户管理', 'user:manage', 1, 0),
('用户查询', 'user:query', 3, 1),
('用户新增', 'user:add', 3, 1),
('用户编辑', 'user:edit', 3, 1),
('用户删除', 'user:delete', 3, 1),
('订单管理', 'order:manage', 1, 0),
('订单查询', 'order:query', 3, 6),
('订单新增', 'order:add', 3, 6),
('订单支付', 'order:pay', 3, 6),
('订单取消', 'order:cancel', 3, 6);

-- 插入角色权限关联数据
-- 管理员拥有所有权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, id FROM `sys_permission`;

-- 普通用户只有查询和基础操作权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
(2, 2), -- 用户查询
(2, 7), -- 订单查询
(2, 8), -- 订单新增
(2, 9), -- 订单支付
(2, 10); -- 订单取消

-- 插入系统用户数据（密码都是 123456）
INSERT INTO `sys_user` (`username`, `password`, `real_name`, `email`, `phone`, `user_type`) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKIdeyZQe5hXpJ8F.cJ8/W8WGZfm', '系统管理员', 'admin@luckyh.com', '13800000001', 1),
('user001', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKIdeyZQe5hXpJ8F.cJ8/W8WGZfm', '张三', 'zhangsan@luckyh.com', '13800000002', 2),
('user002', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKIdeyZQe5hXpJ8F.cJ8/W8WGZfm', '李四', 'lisi@luckyh.com', '13800000003', 2);

-- 插入用户角色关联数据
INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES
(1, 1), -- admin -> 管理员角色
(2, 2), -- user001 -> 普通用户角色
(3, 2); -- user002 -> 普通用户角色

-- 查看认证相关表的统计信息
SELECT '用户数' as type, COUNT(*) as count FROM `sys_user`
UNION ALL
SELECT '角色数' as type, COUNT(*) as count FROM `sys_role`
UNION ALL
SELECT '权限数' as type, COUNT(*) as count FROM `sys_permission`
UNION ALL
SELECT '用户角色关联数' as type, COUNT(*) as count FROM `sys_user_role`
UNION ALL
SELECT '角色权限关联数' as type, COUNT(*) as count FROM `sys_role_permission`;
