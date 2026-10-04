package com.luckyh.cloud.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luckyh.cloud.account.entity.AccountBalanceLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 账户余额变动明细查询与写入.
 *
 * @author heng.wang
 * @since 2026-10-04
 */
@Mapper
public interface AccountBalanceLogMapper extends BaseMapper<AccountBalanceLog> {

    /**
     * 统计当前用户的余额变动明细。
     *
     * @param userId 账户所属用户 ID
     * @return 明细总数
     */
    @Select("SELECT COUNT(*) FROM account_balance_log WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") Long userId);

    /**
     * 按明细 ID 倒序分页，避免同一秒内多次变动的顺序不确定。
     *
     * @param userId 账户所属用户 ID
     * @param offset 当前页偏移量
     * @param size 当前页条数
     * @return 当前页余额变动明细
     */
    @Select("""
            SELECT id, user_id, change_type, change_amount, before_balance, after_balance,
                   order_no, transaction_id, create_time
            FROM account_balance_log
            WHERE user_id = #{userId}
            ORDER BY id DESC
            LIMIT #{offset}, #{size}
            """)
    List<AccountBalanceLog> selectPageByUserId(@Param("userId") Long userId,
                                              @Param("offset") long offset,
                                              @Param("size") long size);
}
