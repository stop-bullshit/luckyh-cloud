package com.luckyh.cloud.account.service;

import com.luckyh.cloud.account.entity.AccountBalance;
import com.luckyh.cloud.account.mapper.AccountBalanceMapper;
import io.seata.core.context.RootContext;
import io.seata.spring.annotation.GlobalLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 账户余额查询、充值与全局事务分支扣款。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    /** 账户余额数据访问。 */
    private final AccountBalanceMapper accountBalanceMapper;

    /**
     * 查询业务用户的账户余额。
     *
     * @param userId 账户所属业务用户 ID
     * @return 账户余额，不存在时返回 null
     */
    public AccountBalance getAccount(Long userId) {
        AccountBalance account = accountBalanceMapper.selectById(userId);
        if (account != null) {
            return account;
        }

        // 逻辑变动: 无账户记录按零余额展示-20261002-1605-01
        AccountBalance zeroBalanceAccount = new AccountBalance();
        zeroBalanceAccount.setUserId(userId);
        zeroBalanceAccount.setBalance(BigDecimal.ZERO);
        return zeroBalanceAccount;
    }

    /**
     * 为已有账户原子增加余额，提交时检查 Seata 全局锁。
     *
     * @param userId 账户所属业务用户 ID
     * @param amount 本次增加的金额
     * @return 账户存在且充值后未超过字段上限时返回 true
     */
    @GlobalLock
    @Transactional(rollbackFor = Exception.class)
    public boolean recharge(Long userId, BigDecimal amount) {
        // 逻辑变动: 账户余额增量充值-20261002-1556-01
        return accountBalanceMapper.recharge(userId, amount) > 0;
    }

    /**
     * 管理端原子扣减账户余额，提交时检查 Seata 全局锁。
     *
     * @param userId 账户所属业务用户 ID
     * @param amount 本次扣减的金额
     * @return 账户存在且余额充足时返回 true
     */
    @GlobalLock
    @Transactional(rollbackFor = Exception.class)
    public boolean deduct(Long userId, BigDecimal amount) {
        // 逻辑变动: 管理端余额扣减-20261002-1645-01
        return accountBalanceMapper.debit(userId, amount) == 1;
    }

    /**
     * 在已有全局事务内扣款，余额判断和扣减由同一条 SQL 完成。
     *
     * @param userId 账户所属业务用户 ID
     * @param amount 扣款金额
     * @return 余额充足且扣款成功时返回 true
     * @throws IllegalStateException 请求未加入全局事务时拒绝扣款
     */
    @Transactional
    public boolean debit(Long userId, BigDecimal amount) {
        // 逻辑变动: 跨服务事务示例-20261002-01
        String xid = RootContext.getXID();
        if (xid == null || xid.isBlank()) {
            throw new IllegalStateException("账户扣款必须在全局事务中执行");
        }
        log.info("method=debit XID={} userId={} amount={}", xid, userId, amount);
        return accountBalanceMapper.debit(userId, amount) == 1;
    }

    /** 在订单退款全局事务内原子退回余额。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean credit(Long userId, BigDecimal amount) {
        // 逻辑变动: 已支付订单分布式退款-20261002-1730-01
        String xid = RootContext.getXID();
        if (xid == null || xid.isBlank()) {
            throw new IllegalStateException("账户退款必须在全局事务中执行");
        }
        int updatedRows = accountBalanceMapper.credit(userId, amount);
        log.info("method=credit XID={} userId={} amount={} rows={}", xid, userId, amount, updatedRows);
        return updatedRows == 1;
    }
}
