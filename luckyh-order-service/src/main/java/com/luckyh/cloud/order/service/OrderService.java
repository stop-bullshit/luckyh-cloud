package com.luckyh.cloud.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.luckyh.cloud.order.dto.OrderDTO;
import com.luckyh.cloud.order.dto.PurchaseDTO;
import com.luckyh.cloud.order.entity.OrderInfo;
import com.luckyh.cloud.order.vo.OrderVO;

/**
 * 订单服务接口
 */
public interface OrderService extends IService<OrderInfo> {

    /**
     * 创建订单
     *
     * @param orderDTO 订单DTO
     * @return 订单ID
     */
    Long createOrder(OrderDTO orderDTO);

    /** 创建已扣库存及余额的购买订单，三个服务一并提交。 */
    Long purchase(PurchaseDTO purchaseDTO);

    /** 在三个写入分支完成后主动抛错，验证全局回滚。 */
    void purchaseWithRollback(PurchaseDTO purchaseDTO);

    /**
     * 根据ID获取订单
     *
     * @param id 订单ID
     * @return 订单VO
     */
    OrderVO getOrderById(Long id);

    /**
     * 分页查询订单
     *
     * @param current 当前页
     * @param size    每页大小
     * @param userId  用户ID（可选）
     * @return 分页结果
     */
    Page<OrderVO> getOrderPage(Long current, Long size, Long userId);

    /**
     * 支付订单
     *
     * @param id 订单ID
     * @return 是否成功
     */
    boolean payOrder(Long id);

    /**
     * 取消订单
     *
     * @param id 订单ID
     * @return 是否成功
     */
    boolean cancelOrder(Long id);

    /**
     * 对已支付订单执行余额和库存全额退款。
     *
     * @param id 订单ID
     * @return 是否成功
     */
    boolean refundOrder(Long id);
}
