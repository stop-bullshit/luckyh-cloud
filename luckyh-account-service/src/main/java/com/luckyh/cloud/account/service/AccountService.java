package com.luckyh.cloud.account.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.luckyh.cloud.account.entity.AccountBalance;
import com.luckyh.cloud.account.entity.AccountBalanceLog;
import com.luckyh.cloud.account.mapper.AccountBalanceLogMapper;
import com.luckyh.cloud.account.mapper.AccountBalanceMapper;
import jakarta.validation.ConstraintViolationException;
import io.seata.core.context.RootContext;
import io.seata.spring.annotation.GlobalLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /** 与余额写入使用同一事务的变动明细。 */
    private final AccountBalanceLogMapper accountBalanceLogMapper;

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
     * 为登录用户列表批量读取余额，未开户用户按零余额展示。
     *
     * @param ids 用户 ID，每次最多 100 个不同的正整数
     * @return 按输入顺序去重后的余额列表，查询不会开户
     */
    public List<AccountBalance> getAccounts(List<Long> ids) {
        // 逻辑变动: 余额列表批量读取避免逐行查询-20261004-1209-01
        if (ids == null || ids.isEmpty()) {
            throw new ConstraintViolationException("请求参数错误", Set.of());
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(ids);
        if (uniqueIds.size() > 100 || uniqueIds.contains(null)) {
            throw new ConstraintViolationException("请求参数错误", Set.of());
        }
        for (Long id : uniqueIds) {
            if (id <= 0) {
                throw new ConstraintViolationException("请求参数错误", Set.of());
            }
        }
        Map<Long, AccountBalance> accountsByUser = new HashMap<>();
        for (AccountBalance account : accountBalanceMapper.selectBalancesByUserIds(new ArrayList<>(uniqueIds))) {
            accountsByUser.put(account.getUserId(), account);
        }
        List<AccountBalance> accounts = new ArrayList<>(uniqueIds.size());
        for (Long id : uniqueIds) {
            AccountBalance account = accountsByUser.get(id);
            if (account == null) {
                account = new AccountBalance();
                account.setUserId(id);
                account.setBalance(BigDecimal.ZERO);
            }
            accounts.add(account);
        }
        return accounts;
    }

    /**
     * 分页查询账户余额变动明细。
     *
     * @param userId 账户所属用户 ID
     * @param current 当前页，从 1 开始
     * @param size 每页条数，范围 1 到 100
     * @return 按最近变动排序的明细分页
     */
    public Page<AccountBalanceLog> getBalanceLogs(Long userId, long current, long size) {
        // 逻辑变动: 余额变动明细-20261004-1148-01
        if (current < 1 || size < 1 || size > 100 || current - 1 > Long.MAX_VALUE / size) {
            throw new ConstraintViolationException("请求参数错误", Set.of());
        }
        long total = accountBalanceLogMapper.countByUserId(userId);
        Page<AccountBalanceLog> page = new Page<>(current, size, total);
        page.setRecords(accountBalanceLogMapper.selectPageByUserId(userId, (current - 1) * size, size));
        return page;
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
        // 逻辑变动: 余额变动明细-20261004-1148-01
        accountBalanceMapper.prepareRecharge(userId);
        BigDecimal before = accountBalanceMapper.selectForUpdate(userId).getBalance();
        boolean recharged = accountBalanceMapper.recharge(userId, amount) > 0;
        if (!recharged) {
            return false;
        }
        BigDecimal after = accountBalanceMapper.selectForUpdate(userId).getBalance();
        BigDecimal changed = after.subtract(before);
        // FOUND_ROWS 下溢出未变动也可能返回成功，保留响应但不制造假明细。
        if (changed.signum() != 0) {
            saveBalanceLog(userId, "RECHARGE", changed, before, after, null);
        }
        return true;
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
        if (accountBalanceMapper.debit(userId, amount) != 1) {
            return false;
        }
        BigDecimal after = accountBalanceMapper.selectForUpdate(userId).getBalance();
        saveBalanceLog(userId, "DEDUCT", amount.negate(), after.add(amount), after, null);
        return true;
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
        return debit(userId, amount, null);
    }

    /**
     * 在已有全局事务内扣款并记录可选订单号。
     *
     * @param userId 账户所属用户 ID
     * @param amount 扣款金额
     * @param orderNo 关联订单号，旧调用可为空
     * @return 余额充足且扣款成功时返回 true
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean debit(Long userId, BigDecimal amount, String orderNo) {
        // 逻辑变动: 跨服务事务示例-20261002-01
        String xid = RootContext.getXID();
        if (xid == null || xid.isBlank()) {
            throw new IllegalStateException("账户扣款必须在全局事务中执行");
        }
        log.info("method=debit XID={} userId={} amount={}", xid, userId, amount);
        if (accountBalanceMapper.debit(userId, amount) != 1) {
            return false;
        }
        BigDecimal after = accountBalanceMapper.selectForUpdate(userId).getBalance();
        saveBalanceLog(userId, "DEBIT", amount.negate(), after.add(amount), after, orderNo);
        return true;
    }

    /** 在订单退款全局事务内原子退回余额。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean credit(Long userId, BigDecimal amount) {
        return credit(userId, amount, null);
    }

    /**
     * 在订单退款全局事务内退回余额并记录可选订单号。
     *
     * @param userId 账户所属用户 ID
     * @param amount 退款金额
     * @param orderNo 关联订单号，旧调用可为空
     * @return 账户存在且退款成功时返回 true
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean credit(Long userId, BigDecimal amount, String orderNo) {
        // 逻辑变动: 已支付订单分布式退款-20261002-1730-01
        String xid = RootContext.getXID();
        if (xid == null || xid.isBlank()) {
            throw new IllegalStateException("账户退款必须在全局事务中执行");
        }
        int updatedRows = accountBalanceMapper.credit(userId, amount);
        log.info("method=credit XID={} userId={} amount={} rows={}", xid, userId, amount, updatedRows);
        if (updatedRows != 1) {
            return false;
        }
        BigDecimal after = accountBalanceMapper.selectForUpdate(userId).getBalance();
        saveBalanceLog(userId, "CREDIT", amount, after.subtract(amount), after, orderNo);
        return true;
    }

    /** 将准确的余额前后值与本次业务写入共同提交或回滚。 */
    private void saveBalanceLog(Long userId, String changeType, BigDecimal changeAmount,
                                BigDecimal before, BigDecimal after, String orderNo) {
        // 逻辑变动: 余额变动明细-20261004-1148-01
        AccountBalanceLog balanceLog = new AccountBalanceLog();
        balanceLog.setUserId(userId);
        balanceLog.setChangeType(changeType);
        balanceLog.setChangeAmount(changeAmount.setScale(2));
        balanceLog.setBeforeBalance(before.setScale(2));
        balanceLog.setAfterBalance(after.setScale(2));
        balanceLog.setOrderNo(orderNo);
        balanceLog.setTransactionId(RootContext.getXID());
        balanceLog.setCreateTime(LocalDateTime.now());
        if (accountBalanceLogMapper.insert(balanceLog) != 1) {
            throw new IllegalStateException("余额明细写入失败");
        }
    }
}
