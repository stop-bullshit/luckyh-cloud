package com.luckyh.cloud.account.service;

import com.luckyh.cloud.account.mapper.AccountBalanceMapper;
import com.luckyh.cloud.account.entity.AccountBalance;
import io.seata.core.context.RootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 全局事务扣款的入口约束与更新结果检查。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountBalanceMapper accountBalanceMapper;

    @InjectMocks
    private AccountService accountService;

    @AfterEach
    void clearGlobalTransaction() {
        RootContext.unbind();
    }

    @Test
    void returnsZeroBalanceWhenUserHasNoAccountRecord() {
        when(accountBalanceMapper.selectById(6L)).thenReturn(null);

        AccountBalance account = accountService.getAccount(6L);

        assertEquals(6L, account.getUserId());
        assertEquals(BigDecimal.ZERO, account.getBalance());
        assertNull(account.getUpdateTime());
        verify(accountBalanceMapper).selectById(6L);
        verifyNoMoreInteractions(accountBalanceMapper);
    }

    @Test
    void rechargesExistingAccountWithAtomicIncrement() {
        BigDecimal amount = new BigDecimal("100.50");
        when(accountBalanceMapper.recharge(6L, amount)).thenReturn(2);

        assertTrue(accountService.recharge(6L, amount));

        verify(accountBalanceMapper).recharge(6L, amount);
        verifyNoMoreInteractions(accountBalanceMapper);
    }

    @Test
    void rejectsRechargeWhenAccountIsMissingOrBalanceWouldOverflow() {
        BigDecimal amount = new BigDecimal("100.50");
        when(accountBalanceMapper.recharge(6L, amount)).thenReturn(0);

        assertFalse(accountService.recharge(6L, amount));

        verify(accountBalanceMapper).recharge(6L, amount);
        verifyNoMoreInteractions(accountBalanceMapper);
    }

    @Test
    void deductsBalanceWithAtomicUpdate() {
        BigDecimal amount = new BigDecimal("20.50");
        when(accountBalanceMapper.debit(6L, amount)).thenReturn(1);

        assertTrue(accountService.deduct(6L, amount));

        verify(accountBalanceMapper).debit(6L, amount);
        verifyNoMoreInteractions(accountBalanceMapper);
    }

    @Test
    void rejectsDeductWhenAccountIsMissingOrBalanceIsInsufficient() {
        BigDecimal amount = new BigDecimal("20.50");
        when(accountBalanceMapper.debit(6L, amount)).thenReturn(0);

        assertFalse(accountService.deduct(6L, amount));

        verify(accountBalanceMapper).debit(6L, amount);
        verifyNoMoreInteractions(accountBalanceMapper);
    }

    @Test
    void debitsWithinGlobalTransaction() {
        RootContext.bind("account-test-success");
        BigDecimal amount = new BigDecimal("12.34");
        when(accountBalanceMapper.debit(6L, amount)).thenReturn(1);

        assertTrue(accountService.debit(6L, amount));

        verify(accountBalanceMapper).debit(6L, amount);
        verifyNoMoreInteractions(accountBalanceMapper);
    }

    @Test
    void rejectsInsufficientBalance() {
        RootContext.bind("account-test-insufficient");
        BigDecimal amount = new BigDecimal("12.34");
        when(accountBalanceMapper.debit(6L, amount)).thenReturn(0);

        assertFalse(accountService.debit(6L, amount));

        verify(accountBalanceMapper).debit(6L, amount);
        verifyNoMoreInteractions(accountBalanceMapper);
    }

    @Test
    void rejectsWritesWithoutGlobalTransaction() {
        RootContext.unbind();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> accountService.debit(6L, new BigDecimal("12.34")));

        assertEquals("账户扣款必须在全局事务中执行", error.getMessage());
        verifyNoInteractions(accountBalanceMapper);
    }
}
