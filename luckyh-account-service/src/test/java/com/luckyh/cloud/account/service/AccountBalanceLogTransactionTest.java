package com.luckyh.cloud.account.service;

import com.luckyh.cloud.account.entity.AccountBalance;
import com.luckyh.cloud.account.entity.AccountBalanceLog;
import com.luckyh.cloud.account.mapper.AccountBalanceLogMapper;
import com.luckyh.cloud.account.mapper.AccountBalanceMapper;
import io.seata.core.context.RootContext;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证旧扣款入口经过 Spring 事务代理，明细失败不会提交余额.
 *
 * @author heng.wang
 * @since 2026-10-04
 */
class AccountBalanceLogTransactionTest {

    @Test
    void legacyDebitRollsBackWhenBalanceLogFails() {
        AccountBalanceMapper balances = mock(AccountBalanceMapper.class);
        AccountBalanceLogMapper logs = mock(AccountBalanceLogMapper.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        SimpleTransactionStatus transaction = new SimpleTransactionStatus();
        when(transactions.getTransaction(any())).thenReturn(transaction);
        when(balances.debit(6L, BigDecimal.ONE)).thenReturn(1);
        AccountBalance after = new AccountBalance();
        after.setBalance(new BigDecimal("99.00"));
        when(balances.selectForUpdate(6L)).thenReturn(after);
        when(logs.insert(any(AccountBalanceLog.class))).thenThrow(new IllegalStateException("ledger failed"));
        ProxyFactory factory = new ProxyFactory(new AccountService(balances, logs));
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        AccountService service = (AccountService) factory.getProxy();

        RootContext.bind("legacy-debit-xid");
        try {
            assertThrows(IllegalStateException.class, () -> service.debit(6L, BigDecimal.ONE));
            verify(transactions).rollback(transaction);
            verify(transactions, never()).commit(any());
        } finally {
            RootContext.unbind();
        }
    }
}
