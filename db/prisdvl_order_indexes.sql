-- ============================================================
-- Prisdvl-Order 索引优化脚本
-- 适用：MySQL 8.0+，库名按实际环境替换（默认 sky_take_out）
-- 说明：以下索引对应订单列表、订单超时扫描、菜单缓存穿透后的回源、
--       批量删除前的 IN 查询等高频 SQL；执行前请确认索引不存在。
-- ============================================================

USE sky_take_out;

-- 订单：用户端历史订单（user_id + status + order_time）
ALTER TABLE orders
    ADD INDEX idx_orders_user_status_time (user_id, status, order_time);

-- 订单：管理端条件搜索、超时任务（status + order_time）
ALTER TABLE orders
    ADD INDEX idx_orders_status_time (status, order_time);

-- 订单：支付回调按商户订单号查询
ALTER TABLE orders
    ADD INDEX idx_orders_number (number);

-- 订单明细：详情页、订单列表 IN 批量查询
ALTER TABLE order_detail
    ADD INDEX idx_order_detail_order_id (order_id);

-- 菜品：用户端按分类查起售菜品并按下单时间排序（category_id + status + create_time）
ALTER TABLE dish
    ADD INDEX idx_dish_category_status_time (category_id, status, create_time);

-- 口味：菜品列表批量 IN 查询
ALTER TABLE dish_flavor
    ADD INDEX idx_dish_flavor_dish_id (dish_id);

-- 套餐：用户端按分类查起售套餐
ALTER TABLE setmeal
    ADD INDEX idx_setmeal_category_status (category_id, status);

-- 套餐-菜品关联：按套餐查菜品、按菜品反查套餐
ALTER TABLE setmeal_dish
    ADD INDEX idx_setmeal_dish_setmeal_id (setmeal_id),
    ADD INDEX idx_setmeal_dish_dish_id (dish_id);

-- 购物车：按用户查购物车、下单时清空购物车
ALTER TABLE shopping_cart
    ADD INDEX idx_shopping_cart_user_id (user_id);

-- 分类：用户端按类型查启用的分类并按 sort 排序
ALTER TABLE category
    ADD INDEX idx_category_status_type_sort (status, type, sort, create_time);

-- 员工/用户登录查询
ALTER TABLE employee
    ADD INDEX idx_employee_username (username);

ALTER TABLE user
    ADD INDEX idx_user_openid (openid);

-- 执行后可用以下语句验证：
-- EXPLAIN SELECT * FROM orders WHERE user_id = 1 AND status = 2 ORDER BY order_time DESC LIMIT 10;
-- EXPLAIN SELECT * FROM order_detail WHERE order_id IN (1,2,3);
-- EXPLAIN SELECT * FROM dish WHERE category_id = 1 AND status = 1 ORDER BY create_time DESC;
-- EXPLAIN SELECT * FROM orders WHERE status = 1 AND order_time < '2026-01-01 00:00:00' ORDER BY id LIMIT 500;
