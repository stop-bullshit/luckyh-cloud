package com.luckyh.cloud.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luckyh.cloud.account.entity.AccountBalance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 账户余额数据访问。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@Mapper
public interface AccountBalanceMapper extends BaseMapper<AccountBalance> {

    /**
     * 首次充值创建账户，已有账户原子增加余额，并限制结果不超过 DECIMAL(18,2) 上限。
     *
     * @param userId 账户所属业务用户 ID
     * @param amount 本次增加的金额
     * @return 实际更新的账户数量
     */
    @org.apache.ibatis.annotations.Insert("""
            INSERT INTO account_balance (user_id, balance)
            VALUES (#{userId}, #{amount})
            ON DUPLICATE KEY UPDATE
            balance = IF(balance <= 9999999999999999.99 - VALUES(balance),
                         balance + VALUES(balance), balance)
            """)
    int recharge(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 余额充足时原子扣款，避免并发请求使余额变为负数。
     *
     * @param userId 账户所属业务用户 ID
     * @param amount 扣款金额
     * @return 实际更新的账户数量
     */
    @Update("""
            UPDATE account_balance
            SET balance = balance - #{amount}, update_time = CURRENT_TIMESTAMP
            WHERE user_id = #{userId} AND balance >= #{amount}
            """)
    int debit(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /** 账户存在且增加后不超过字段上限时退回余额。 */
    @Update("""
            UPDATE account_balance
            SET balance = balance + #{amount}, update_time = CURRENT_TIMESTAMP
            WHERE user_id = #{userId} AND balance <= 9999999999999999.99 - #{amount}
            """)
    int credit(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
