-- 已有 luckyh_cloud 库升级：订单保存商品ID，供支付时调用库存服务。
-- 历史订单无法可靠按商品名称匹配，因此 product_id 保持 NULL，支付时会明确拒绝。
USE `luckyh_cloud`;

ALTER TABLE `order_info`
  ADD COLUMN `product_id` bigint DEFAULT NULL COMMENT '商品ID' AFTER `user_id`,
  ADD KEY `idx_product_id` (`product_id`);
