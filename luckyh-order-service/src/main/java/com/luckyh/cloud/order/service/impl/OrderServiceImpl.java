package com.luckyh.cloud.order.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
// import com.luckyh.cloud.common.mq.producer.MessageProducer;
// import com.luckyh.cloud.common.mq.message.OrderCreateMessage;
// import com.luckyh.cloud.common.mq.message.OrderPaymentMessage;
import com.luckyh.cloud.common.core.domain.Result;
// import com.luckyh.cloud.common.trace.util.TraceUtils;
import com.luckyh.cloud.order.dto.OrderDTO;
import com.luckyh.cloud.order.dto.AccountDebitRequest;
import com.luckyh.cloud.order.dto.AccountCreditRequest;
import com.luckyh.cloud.order.dto.InventoryDeductRequest;
import com.luckyh.cloud.order.dto.InventoryRestoreRequest;
import com.luckyh.cloud.order.dto.PurchaseDTO;
import com.luckyh.cloud.order.entity.OrderInfo;
import com.luckyh.cloud.order.entity.OrderOperationLog;
import com.luckyh.cloud.order.exception.PurchaseRollbackException;
import com.luckyh.cloud.order.feign.AccountServiceFeign;
import com.luckyh.cloud.order.feign.InventoryServiceFeign;
import com.luckyh.cloud.order.feign.UserServiceFeign;
import com.luckyh.cloud.order.mapper.OrderMapper;
import com.luckyh.cloud.order.mapper.OrderOperationLogMapper;
import com.luckyh.cloud.order.service.OrderService;
import com.luckyh.cloud.order.vo.OrderVO;
import com.luckyh.cloud.order.vo.InventoryProductVO;
import io.seata.core.context.RootContext;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, OrderInfo> implements OrderService {

    private final UserServiceFeign userServiceFeign;
    private final InventoryServiceFeign inventoryServiceFeign;
    private final AccountServiceFeign accountServiceFeign;
    private final OrderOperationLogMapper orderOperationLogMapper;
    // private final MessageProducer messageProducer;

    private static final Map<Integer, String> STATUS_MAP = new HashMap<>();

    static {
        STATUS_MAP.put(0, "待支付");
        STATUS_MAP.put(1, "已支付");
        STATUS_MAP.put(2, "已取消");
        STATUS_MAP.put(3, "已退款");
    }

    @Override
    @GlobalTransactional(name = "order-create-tx", rollbackFor = Exception.class)
    public Long createOrder(OrderDTO orderDTO) {
        // String traceId = TraceUtils.getTraceId();
        // log.info("开始创建订单，traceId: {}", traceId);
        log.info("开始创建订单，启用分布式事务");

        // 逻辑变动: 订单统一关联登录用户-20261002-1735-01
        // 检查登录用户是否存在
        Result<OrderVO.UserInfo> userResult = userServiceFeign.getUserById(orderDTO.getUserId());
        if (userResult.getCode() != 200 || userResult.getData() == null) {
            log.warn("用户查询失败，无法创建订单，用户ID：{}，原因：{}", orderDTO.getUserId(), userResult.getMessage());
            throw new IllegalStateException("用户查询失败：" + userResult.getMessage());
        }

        // 逻辑变动: 订单商品来源统一-20261002-1745-01
        Result<InventoryProductVO> productResult = inventoryServiceFeign.getProductById(orderDTO.getProductId());
        if (productResult.getCode() != 200 || productResult.getData() == null) {
            throw new IllegalStateException("商品查询失败：" + productResult.getMessage());
        }
        InventoryProductVO product = productResult.getData();

        OrderInfo orderInfo = new OrderInfo();
        BeanUtil.copyProperties(orderDTO, orderInfo);
        orderInfo.setProductId(orderDTO.getProductId());
        orderInfo.setProductName(product.getProductName());
        orderInfo.setProductPrice(product.getProductPrice());

        // 生成订单号
        orderInfo.setOrderNo("ORDER_" + IdUtil.getSnowflakeNextIdStr());

        // 计算总金额
        BigDecimal totalAmount = product.getProductPrice()
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
        saveOperationLog(orderInfo, "CREATE", null, 0);

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
    @GlobalTransactional(name = "order-purchase-tx", rollbackFor = Exception.class)
    public Long purchase(PurchaseDTO purchaseDTO) {
        return executePurchase(purchaseDTO);
    }

    @Override
    @GlobalTransactional(name = "order-purchase-rollback-tx", rollbackFor = Exception.class)
    public void purchaseWithRollback(PurchaseDTO purchaseDTO) {
        Long orderId = executePurchase(purchaseDTO);
        throw new PurchaseRollbackException(orderId, RootContext.getXID());
    }

    /** 依次完成订单、库存、账户写入，任一业务失败都向全局事务抛出异常。 */
    private Long executePurchase(PurchaseDTO purchaseDTO) {
        // 逻辑变动: 跨服务事务示例-20261002-01
        String xid = RootContext.getXID();
        if (xid == null || xid.isBlank()) {
            throw new IllegalStateException("购买必须在Seata全局事务内执行");
        }
        log.info("开始跨服务购买 xid={} userId={} productId={} quantity={}",
                xid, purchaseDTO.getUserId(), purchaseDTO.getProductId(), purchaseDTO.getQuantity());
        Result<OrderVO.UserInfo> userResult = userServiceFeign.getUserById(purchaseDTO.getUserId());
        if (userResult.getCode() != 200 || userResult.getData() == null) {
            throw new IllegalStateException("用户查询失败：" + userResult.getMessage());
        }
        Result<InventoryProductVO> productResult = inventoryServiceFeign.getProductById(purchaseDTO.getProductId());
        if (productResult.getCode() != 200 || productResult.getData() == null) {
            throw new IllegalStateException("商品查询失败：" + productResult.getMessage());
        }

        InventoryProductVO product = productResult.getData();
        BigDecimal amount = product.getProductPrice().multiply(BigDecimal.valueOf(purchaseDTO.getQuantity()));
        OrderInfo order = new OrderInfo();
        order.setOrderNo("PURCHASE_" + IdUtil.getSnowflakeNextIdStr());
        order.setUserId(purchaseDTO.getUserId());
        order.setProductId(purchaseDTO.getProductId());
        order.setProductName(product.getProductName());
        order.setProductPrice(product.getProductPrice());
        order.setQuantity(purchaseDTO.getQuantity());
        order.setTotalAmount(amount);
        order.setStatus(1);
        order.setPayTime(LocalDateTime.now());
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(order.getCreateTime());
        if (!save(order)) {
            throw new IllegalStateException("购买订单写入失败");
        }
        log.info("订单分支已写入 xid={} orderId={}", xid, order.getId());
        saveOperationLog(order, "PURCHASE", null, 1);

        Result<Void> inventoryResult = inventoryServiceFeign.deduct(
                new InventoryDeductRequest(purchaseDTO.getProductId(), purchaseDTO.getQuantity()));
        if (inventoryResult.getCode() != 200) {
            throw new IllegalStateException("库存扣减失败：" + inventoryResult.getMessage());
        }
        Result<Void> accountResult = accountServiceFeign.debit(new AccountDebitRequest(purchaseDTO.getUserId(), amount));
        if (accountResult.getCode() != 200) {
            throw new IllegalStateException("账户扣款失败：" + accountResult.getMessage());
        }
        log.info("三个购买分支操作完成 xid={} orderId={} userId={} productId={} amount={}",
                xid, order.getId(), purchaseDTO.getUserId(), purchaseDTO.getProductId(), amount);
        return order.getId();
    }

    @Override
    public OrderVO getOrderById(Long id) {
        OrderInfo orderInfo = getById(id);
        if (orderInfo == null) {
            log.warn("订单不存在，订单ID：{}", id);
            return null;
        }

        OrderVO orderVO = buildOrderVO(orderInfo);
        Result<OrderVO.UserInfo> userResult = userServiceFeign.getUserById(orderInfo.getUserId());
        if (userResult.getCode() == 200) {
            orderVO.setUserInfo(userResult.getData());
        }
        orderVO.setOperationLogs(orderOperationLogMapper.selectList(
                        new LambdaQueryWrapper<OrderOperationLog>()
                                .eq(OrderOperationLog::getOrderId, id)
                                .orderByAsc(OrderOperationLog::getCreateTime))
                .stream()
                .map(operation -> {
                    OrderVO.OrderOperation item = new OrderVO.OrderOperation();
                    BeanUtil.copyProperties(operation, item);
                    return item;
                })
                .toList());
        return orderVO;
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

        // 逻辑变动: 订单分页批量关联登录用户-20261002-1735-01
        List<Long> userIds = resultPage.getRecords().stream()
                .map(OrderInfo::getUserId)
                .distinct()
                .toList();
        Map<Long, OrderVO.UserInfo> users = new HashMap<>();
        if (!userIds.isEmpty()) {
            Result<List<OrderVO.UserInfo>> usersResult = userServiceFeign.getUsersByIds(userIds);
            if (usersResult.getCode() == 200 && usersResult.getData() != null) {
                users = usersResult.getData().stream()
                        .collect(Collectors.toMap(OrderVO.UserInfo::getId, Function.identity()));
            }
        }
        Map<Long, OrderVO.UserInfo> userMap = users;
        Page<OrderVO> voPage = new Page<>(current, size, resultPage.getTotal());
        voPage.setRecords(resultPage.getRecords().stream()
                .map(order -> {
                    OrderVO orderVO = buildOrderVO(order);
                    orderVO.setUserInfo(userMap.get(order.getUserId()));
                    return orderVO;
                })
                .toList());

        return voPage;
    }

    @Override
    @GlobalTransactional(name = "order-pay-tx", rollbackFor = Exception.class)
    public boolean payOrder(Long id) {
        // 逻辑变动: 普通订单支付接入库存余额分布式扣减-20261002-1710-01
        OrderInfo orderInfo = getById(id);
        if (orderInfo == null) {
            log.warn("订单不存在，订单ID：{}", id);
            return false;
        }

        if (orderInfo.getStatus() != 0) {
            log.warn("订单状态不正确，无法支付，订单ID：{}，当前状态：{}", id, orderInfo.getStatus());
            return false;
        }

        if (orderInfo.getProductId() == null) {
            throw new IllegalStateException("历史订单缺少商品ID，无法扣减库存和余额");
        }

        String xid = RootContext.getXID();
        if (xid == null || xid.isBlank()) {
            throw new IllegalStateException("订单支付必须在Seata全局事务内执行");
        }

        boolean statusChanged = lambdaUpdate()
                .eq(OrderInfo::getId, id)
                .eq(OrderInfo::getStatus, 0)
                .set(OrderInfo::getStatus, 1)
                .set(OrderInfo::getPayTime, LocalDateTime.now())
                .set(OrderInfo::getUpdateTime, LocalDateTime.now())
                .update();
        if (!statusChanged) {
            log.warn("订单状态已变化，无法重复支付，订单ID：{}", id);
            return false;
        }

        Result<Void> inventoryResult = inventoryServiceFeign.deduct(
                new InventoryDeductRequest(orderInfo.getProductId(), orderInfo.getQuantity()));
        if (inventoryResult.getCode() != 200) {
            throw new IllegalStateException("库存扣减失败：" + inventoryResult.getMessage());
        }

        Result<Void> accountResult = accountServiceFeign.debit(
                new AccountDebitRequest(orderInfo.getUserId(), orderInfo.getTotalAmount()));
        if (accountResult.getCode() != 200) {
            throw new IllegalStateException("账户扣款失败：" + accountResult.getMessage());
        }

        saveOperationLog(orderInfo, "PAY", 0, 1);

        log.info("订单支付完成 xid={} orderId={} userId={} productId={} quantity={} amount={}",
                xid, id, orderInfo.getUserId(), orderInfo.getProductId(),
                orderInfo.getQuantity(), orderInfo.getTotalAmount());
        return true;
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

        // 逻辑变动: 订单状态流转原子化并记录流水-20261002-1730-02
        boolean updated = lambdaUpdate()
                .eq(OrderInfo::getId, id)
                .eq(OrderInfo::getStatus, 0)
                .set(OrderInfo::getStatus, 2)
                .set(OrderInfo::getCancelTime, LocalDateTime.now())
                .set(OrderInfo::getUpdateTime, LocalDateTime.now())
                .update();
        if (updated) {
            saveOperationLog(orderInfo, "CANCEL", 0, 2);
            log.info("订单取消成功，订单ID：{}", id);
        } else {
            log.error("订单取消失败，订单ID：{}", id);
            throw new RuntimeException("订单取消失败");
        }

        return updated;
    }

    @Override
    @GlobalTransactional(name = "order-refund-tx", rollbackFor = Exception.class)
    public boolean refundOrder(Long id) {
        // 逻辑变动: 已支付订单分布式退款-20261002-1730-01
        OrderInfo orderInfo = getById(id);
        if (orderInfo == null || orderInfo.getStatus() != 1) {
            return false;
        }
        if (orderInfo.getProductId() == null) {
            throw new IllegalStateException("订单缺少商品ID，无法返还库存");
        }
        String xid = RootContext.getXID();
        if (xid == null || xid.isBlank()) {
            throw new IllegalStateException("订单退款必须在Seata全局事务内执行");
        }

        boolean statusChanged = lambdaUpdate()
                .eq(OrderInfo::getId, id)
                .eq(OrderInfo::getStatus, 1)
                .set(OrderInfo::getStatus, 3)
                .set(OrderInfo::getRefundTime, LocalDateTime.now())
                .set(OrderInfo::getUpdateTime, LocalDateTime.now())
                .update();
        if (!statusChanged) {
            return false;
        }

        Result<Void> accountResult = accountServiceFeign.credit(
                new AccountCreditRequest(orderInfo.getUserId(), orderInfo.getTotalAmount()));
        if (accountResult.getCode() != 200) {
            throw new IllegalStateException("账户退款失败：" + accountResult.getMessage());
        }
        Result<Void> inventoryResult = inventoryServiceFeign.restore(
                new InventoryRestoreRequest(orderInfo.getProductId(), orderInfo.getQuantity()));
        if (inventoryResult.getCode() != 200) {
            throw new IllegalStateException("库存返还失败：" + inventoryResult.getMessage());
        }

        saveOperationLog(orderInfo, "REFUND", 1, 3);
        log.info("订单退款完成 xid={} orderId={} userId={} productId={} quantity={} amount={}",
                xid, id, orderInfo.getUserId(), orderInfo.getProductId(),
                orderInfo.getQuantity(), orderInfo.getTotalAmount());
        return true;
    }

    /**
     * 构建订单VO
     */
    private OrderVO buildOrderVO(OrderInfo orderInfo) {
        OrderVO orderVO = new OrderVO();
        BeanUtil.copyProperties(orderInfo, orderVO);
        orderVO.setStatusDesc(STATUS_MAP.get(orderInfo.getStatus()));

        return orderVO;
    }

    /** 保存与订单本地写入共同提交或回滚的状态操作流水。 */
    private void saveOperationLog(OrderInfo orderInfo, String operationType,
                                  Integer fromStatus, Integer toStatus) {
        OrderOperationLog operationLog = new OrderOperationLog();
        operationLog.setOrderId(orderInfo.getId());
        operationLog.setOrderNo(orderInfo.getOrderNo());
        operationLog.setOperationType(operationType);
        operationLog.setFromStatus(fromStatus);
        operationLog.setToStatus(toStatus);
        operationLog.setXid(RootContext.getXID());
        operationLog.setCreateTime(LocalDateTime.now());
        if (orderOperationLogMapper.insert(operationLog) != 1) {
            throw new IllegalStateException("订单操作流水写入失败");
        }
    }
}
