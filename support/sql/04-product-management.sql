-- 商品管理-20261002-01：已有库存库升级，新增商品由数据库生成编号。
-- 先执行 03-distributed-demo.sql；在没有进行中购买事务时执行本脚本。
-- 保留已有商品编号、名称、价格和库存；不会重新插入或重置演示数据。
USE `luckyh_inventory`;

ALTER TABLE `inventory`
  MODIFY COLUMN `product_id` bigint NOT NULL AUTO_INCREMENT COMMENT '商品ID';
