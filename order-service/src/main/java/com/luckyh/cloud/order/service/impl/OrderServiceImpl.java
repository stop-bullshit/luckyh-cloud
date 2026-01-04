package com.luckyh.cloud.order.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
// import com.luckyh.cloud.common.mq.producer.MessageProducer;
// import com.luckyh.cloud.common.mq.message.OrderCreateMessage;
// import com.luckyh.cloud.common.mq.message.OrderPaymentMessage;
import com.luckyh.cloud.order.common.Result;
// import com.luckyh.cloud.common.trace.util.TraceUtils;
import com.luckyh.cloud.order.dto.OrderDTO;
import com.luckyh.cloud.order.entity.OrderInfo;
import com.luckyh.cloud.order.feign.UserServiceFeign;
import com.luckyh.cloud.order.mapper.OrderMapper;
import com.luckyh.cloud.order.service.OrderService;
import com.luckyh.cloud.order.vo.OrderVO;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 订单服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, OrderInfo> implements OrderService {

    private final UserServiceFeign userServiceFeign;
    // private final MessageProducer messageProducer;

    private static final Map<Integer, String> STATUS_MAP = new HashMap<>();

    static {
        STATUS_MAP.put(0, "待支付");
        STATUS_MAP.put(1, "已支付");
        STATUS_MAP.put(2, "已取消");
    }

    @Override
    @GlobalTransactional(name = "order-create-tx", rollbackFor = Exception.class)
    public Long createOrder(OrderDTO orderDTO) {
        // String traceId = TraceUtils.getTraceId();
        // log.info("开始创建订单，traceId: {}", traceId);
        log.info("开始创建订单，启用分布式事务");

        // 检查用户是否存在
        Result<OrderVO.UserInfo> userResult = userServiceFeign.getUserById(orderDTO.getUserId());
        if (userResult.getCode() != 200 || userResult.getData() == null) {
            log.warn("用户不存在，无法创建订单，用户ID：{}", orderDTO.getUserId());
            throw new RuntimeException("用户不存在");
        }

        OrderInfo orderInfo = new OrderInfo();
        BeanUtil.copyProperties(orderDTO, orderInfo);

        // 生成订单号
        orderInfo.setOrderNo("ORDER_" + IdUtil.getSnowflakeNextIdStr());

        // 计算总金额
        BigDecimal totalAmount = orderDTO.getProductPrice()
                .multiply(new BigDecimal(orderDTO.getQuantity()));
        orderInfo.setTotalAmount(totalAmount);

        // 设置订单状态为待支付
        orderInfo.setStatus(0);
        orderInfo.setCreateTime(LocalDateTime.now());
        orderInfo.setUpdateTime(LocalDateTime.now());

        boolean saved = save(orderInfo);
        if (!saved) {
            log.error("订单创建失败");
            throw new RuntimeException("订单创建失败");
        }

        log.info("订单创建成功，订单ID：{}，订单号：{}", orderInfo.getId(), orderInfo.getOrderNo());

        // 发送订单创建消息 - 暂时注释掉，待实现MQ模块
        // OrderCreateMessage message = new OrderCreateMessage();
        // message.setOrderId(orderInfo.getId());
        // message.setOrderNo(orderInfo.getOrderNo());
        // message.setUserId(orderInfo.getUserId());
        // message.setProductName(orderInfo.getProductName());
        // message.setQuantity(orderInfo.getQuantity());
        // message.setPrice(orderInfo.getProductPrice());
        // message.setTotalAmount(orderInfo.getTotalAmount());
        // message.setTraceId(traceId);

        // messageProducer.sendOrderCreateMessage(message);
        // log.info("订单创建消息已发送，订单ID：{}, traceId: {}", orderInfo.getId(), traceId);

        return orderInfo.getId();
    }

    @Override
    public OrderVO getOrderById(Long id) {
        OrderInfo orderInfo = getById(id);
        if (orderInfo == null) {
            log.warn("订单不存在，订单ID：{}", id);
            return null;
        }

        return buildOrderVO(orderInfo);
    }

    @Override
    public Page<OrderVO> getOrderPage(Long current, Long size, Long userId) {
        Page<OrderInfo> orderPage = new Page<>(current, size);

        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            queryWrapper.eq(OrderInfo::getUserId, userId);
        }
        queryWrapper.orderByDesc(OrderInfo::getCreateTime);

        Page<OrderInfo> resultPage = page(orderPage, queryWrapper);

        Page<OrderVO> voPage = new Page<>(current, size, resultPage.getTotal());
        voPage.setRecords(
                resultPage.getRecords().stream()
                        .map(this::buildOrderVO)
                        .toList());

        return voPage;
    }

    @Override
    @GlobalTransactional(name = "order-pay-tx", rollbackFor = Exception.class)
    public boolean payOrder(Long id) {
        OrderInfo orderInfo = getById(id);
        if (orderInfo == null) {
            log.warn("订单不存在，订单ID：{}", id);
            return false;
        }

        if (orderInfo.getStatus() != 0) {
            log.warn("订单状态不正确，无法支付，订单ID：{}，当前状态：{}", id, orderInfo.getStatus());
            return false;
        }

        orderInfo.setStatus(1);
        orderInfo.setUpdateTime(LocalDateTime.now());

        boolean updated = updateById(orderInfo);
        if (updated) {
            log.info("订单支付成功，订单ID：{}", id);
        } else {
            log.error("订单支付失败，订单ID：{}", id);
            throw new RuntimeException("订单支付失败");
        }

        return updated;
    }

    @Override
    @GlobalTransactional(name = "order-cancel-tx", rollbackFor = Exception.class)
    public boolean cancelOrder(Long id) {
        OrderInfo orderInfo = getById(id);
        if (orderInfo == null) {
            log.warn("订单不存在，订单ID：{}", id);
            return false;
        }

        if (orderInfo.getStatus() != 0) {
            log.warn("订单状态不正确，无法取消，订单ID：{}，当前状态：{}", id, orderInfo.getStatus());
            return false;
        }

        orderInfo.setStatus(2);
        orderInfo.setUpdateTime(LocalDateTime.now());

        boolean updated = updateById(orderInfo);
        if (updated) {
            log.info("订单取消成功，订单ID：{}", id);
        } else {
            log.error("订单取消失败，订单ID：{}", id);
            throw new RuntimeException("订单取消失败");
        }

        return updated;
    }

    /**
     * 构建订单VO
     */
    private OrderVO buildOrderVO(OrderInfo orderInfo) {
        OrderVO orderVO = new OrderVO();
        BeanUtil.copyProperties(orderInfo, orderVO);
        orderVO.setStatusDesc(STATUS_MAP.get(orderInfo.getStatus()));

        // 获取用户信息
        try {
            Result<OrderVO.UserInfo> userResult = userServiceFeign.getUserById(orderInfo.getUserId());
            if (userResult.getCode() == 200 && userResult.getData() != null) {
                orderVO.setUserInfo(userResult.getData());
            }
        } catch (Exception e) {
            log.warn("获取用户信息失败，订单ID：{}，用户ID：{}", orderInfo.getId(), orderInfo.getUserId(), e);
        }

        return orderVO;
    }
}
