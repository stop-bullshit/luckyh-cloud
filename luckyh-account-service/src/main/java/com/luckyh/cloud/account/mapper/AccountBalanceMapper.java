package com.luckyh.cloud.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luckyh.cloud.account.entity.AccountBalance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 账户余额数据访问。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@Mapper
public interface AccountBalanceMapper extends BaseMapper<AccountBalance> {

    /**
     * 一次读取当前用户页的余额，不在列表中逐用户查询。
     *
     * @param ids 已校验且去重的登录用户 ID
     * @return 已开户的账户记录
     */
    @Select("""
            <script>
            SELECT user_id, balance, update_time
            FROM account_balance
            WHERE user_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
            </foreach>
            </script>
            """)
    List<AccountBalance> selectBalancesByUserIds(@Param("ids") List<Long> ids);

    /**
     * 先建立或锁定充值账户，避免缺行查询产生的间隙锁升级冲突。
     *
     * @param userId 账户所属用户 ID
     * @return 数据库报告的影响行数
     */
    @org.apache.ibatis.annotations.Insert("""
            INSERT INTO account_balance (user_id, balance)
            VALUES (#{userId}, 0)
            ON DUPLICATE KEY UPDATE user_id = user_id
            """)
    int prepareRecharge(@Param("userId") Long userId);

    /**
     * 在当前事务持有账户行锁时读取余额，供准确记录变动前后值。
     *
     * @param userId 账户所属用户 ID
     * @return 被锁定的账户记录
     */
    @Select("""
            SELECT user_id, balance, update_time
            FROM account_balance WHERE user_id = #{userId} FOR UPDATE
            """)
    AccountBalance selectForUpdate(@Param("userId") Long userId);

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
