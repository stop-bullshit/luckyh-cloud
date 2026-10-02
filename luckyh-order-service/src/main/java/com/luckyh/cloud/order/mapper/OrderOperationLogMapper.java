package com.luckyh.cloud.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luckyh.cloud.order.entity.OrderOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单操作流水数据访问.
 *
 * @author heng.wang
 * @since 2026-10-02
 */
@Mapper
public interface OrderOperationLogMapper extends BaseMapper<OrderOperationLog> {
}
