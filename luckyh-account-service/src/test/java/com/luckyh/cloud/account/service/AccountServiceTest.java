package com.luckyh.cloud.account.service;

import com.luckyh.cloud.account.mapper.AccountBalanceMapper;
import com.luckyh.cloud.account.mapper.AccountBalanceLogMapper;
import com.luckyh.cloud.account.entity.AccountBalance;
import com.luckyh.cloud.account.entity.AccountBalanceLog;
import jakarta.validation.ConstraintViolationException;
import io.seata.core.context.RootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;

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

    @Mock
    private AccountBalanceLogMapper accountBalanceLogMapper;

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
        when(accountBalanceMapper.selectForUpdate(6L)).thenReturn(account("10.00"), account("110.50"));
        when(accountBalanceLogMapper.insert(any(AccountBalanceLog.class))).thenReturn(1);

        assertTrue(accountService.recharge(6L, amount));

        verify(accountBalanceMapper).recharge(6L, amount);
        verify(accountBalanceMapper).prepareRecharge(6L);
        ArgumentCaptor<AccountBalanceLog> captured = ArgumentCaptor.forClass(AccountBalanceLog.class);
        verify(accountBalanceLogMapper).insert(captured.capture());
        assertEquals("RECHARGE", captured.getValue().getChangeType());
        assertEquals(new BigDecimal("10.00"), captured.getValue().getBeforeBalance());
        assertEquals(new BigDecimal("110.50"), captured.getValue().getAfterBalance());
        assertEquals(new BigDecimal("100.50"), captured.getValue().getChangeAmount());
    }

    @Test
    void rejectsRechargeWhenAccountIsMissingOrBalanceWouldOverflow() {
        BigDecimal amount = new BigDecimal("100.50");
        when(accountBalanceMapper.recharge(6L, amount)).thenReturn(0);
        when(accountBalanceMapper.selectForUpdate(6L)).thenReturn(account("9999999999999999.99"));

        assertFalse(accountService.recharge(6L, amount));

        verify(accountBalanceMapper).recharge(6L, amount);
        verifyNoInteractions(accountBalanceLogMapper);
    }

    @Test
    void deductsBalanceWithAtomicUpdate() {
        BigDecimal amount = new BigDecimal("20.50");
        when(accountBalanceMapper.debit(6L, amount)).thenReturn(1);
        when(accountBalanceMapper.selectForUpdate(6L)).thenReturn(account("79.50"));
        when(accountBalanceLogMapper.insert(any(AccountBalanceLog.class))).thenReturn(1);

        assertTrue(accountService.deduct(6L, amount));

        verify(accountBalanceMapper).debit(6L, amount);
        ArgumentCaptor<AccountBalanceLog> captured = ArgumentCaptor.forClass(AccountBalanceLog.class);
        verify(accountBalanceLogMapper).insert(captured.capture());
        assertEquals("DEDUCT", captured.getValue().getChangeType());
        assertEquals(new BigDecimal("-20.50"), captured.getValue().getChangeAmount());
        assertEquals(new BigDecimal("100.00"), captured.getValue().getBeforeBalance());
        assertEquals(new BigDecimal("79.50"), captured.getValue().getAfterBalance());
    }

    @Test
    void rejectsDeductWhenAccountIsMissingOrBalanceIsInsufficient() {
        BigDecimal amount = new BigDecimal("20.50");
        when(accountBalanceMapper.debit(6L, amount)).thenReturn(0);

        assertFalse(accountService.deduct(6L, amount));

        verify(accountBalanceMapper).debit(6L, amount);
        verifyNoMoreInteractions(accountBalanceMapper);
        verifyNoInteractions(accountBalanceLogMapper);
    }

    @Test
    void debitsWithinGlobalTransaction() {
        RootContext.bind("account-test-success");
        BigDecimal amount = new BigDecimal("12.34");
        when(accountBalanceMapper.debit(6L, amount)).thenReturn(1);
        when(accountBalanceMapper.selectForUpdate(6L)).thenReturn(account("87.66"));
        when(accountBalanceLogMapper.insert(any(AccountBalanceLog.class))).thenReturn(1);

        assertTrue(accountService.debit(6L, amount, "ORDER_6"));

        verify(accountBalanceMapper).debit(6L, amount);
        ArgumentCaptor<AccountBalanceLog> captured = ArgumentCaptor.forClass(AccountBalanceLog.class);
        verify(accountBalanceLogMapper).insert(captured.capture());
        assertEquals("DEBIT", captured.getValue().getChangeType());
        assertEquals("ORDER_6", captured.getValue().getOrderNo());
        assertEquals("account-test-success", captured.getValue().getTransactionId());
        assertEquals(new BigDecimal("100.00"), captured.getValue().getBeforeBalance());
        assertEquals(new BigDecimal("-12.34"), captured.getValue().getChangeAmount());
    }

    @Test
    void rejectsInsufficientBalance() {
        RootContext.bind("account-test-insufficient");
        BigDecimal amount = new BigDecimal("12.34");
        when(accountBalanceMapper.debit(6L, amount)).thenReturn(0);

        assertFalse(accountService.debit(6L, amount));

        verify(accountBalanceMapper).debit(6L, amount);
        verifyNoMoreInteractions(accountBalanceMapper);
        verifyNoInteractions(accountBalanceLogMapper);
    }

    @Test
    void rejectsWritesWithoutGlobalTransaction() {
        RootContext.unbind();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> accountService.debit(6L, new BigDecimal("12.34")));

        assertEquals("账户扣款必须在全局事务中执行", error.getMessage());
        verifyNoInteractions(accountBalanceMapper);
        verifyNoInteractions(accountBalanceLogMapper);
    }

    @Test
    void rechargeOverflowFoundRowsSuccessDoesNotInventALog() {
        when(accountBalanceMapper.selectForUpdate(6L))
                .thenReturn(account("9999999999999999.99"), account("9999999999999999.99"));
        when(accountBalanceMapper.recharge(6L, BigDecimal.ONE)).thenReturn(1);

        assertTrue(accountService.recharge(6L, BigDecimal.ONE));
        verifyNoInteractions(accountBalanceLogMapper);
    }

    @Test
    void creditsOrderRefundWithinGlobalTransaction() {
        RootContext.bind("account-refund-xid");
        when(accountBalanceMapper.credit(6L, BigDecimal.ONE)).thenReturn(1);
        when(accountBalanceMapper.selectForUpdate(6L)).thenReturn(account("101.00"));
        when(accountBalanceLogMapper.insert(any(AccountBalanceLog.class))).thenReturn(1);

        assertTrue(accountService.credit(6L, BigDecimal.ONE, "ORDER_6"));
        ArgumentCaptor<AccountBalanceLog> captured = ArgumentCaptor.forClass(AccountBalanceLog.class);
        verify(accountBalanceLogMapper).insert(captured.capture());
        assertEquals("CREDIT", captured.getValue().getChangeType());
        assertEquals("ORDER_6", captured.getValue().getOrderNo());
        assertEquals("account-refund-xid", captured.getValue().getTransactionId());
        assertEquals(new BigDecimal("1.00"), captured.getValue().getChangeAmount());
        assertEquals(new BigDecimal("100.00"), captured.getValue().getBeforeBalance());
        assertEquals(new BigDecimal("101.00"), captured.getValue().getAfterBalance());
    }

    @Test
    void failedRefundDoesNotWriteALog() {
        RootContext.bind("account-refund-xid");
        when(accountBalanceMapper.credit(6L, BigDecimal.ONE)).thenReturn(0);

        assertFalse(accountService.credit(6L, BigDecimal.ONE));
        verify(accountBalanceMapper, never()).selectForUpdate(6L);
        verifyNoInteractions(accountBalanceLogMapper);
    }

    @Test
    void failedLogInsertEscapesTheTransactionBoundary() {
        when(accountBalanceMapper.debit(6L, BigDecimal.ONE)).thenReturn(1);
        when(accountBalanceMapper.selectForUpdate(6L)).thenReturn(account("99.00"));
        when(accountBalanceLogMapper.insert(any(AccountBalanceLog.class))).thenReturn(0);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> accountService.deduct(6L, BigDecimal.ONE));
        assertEquals("余额明细写入失败", failure.getMessage());
    }

    @Test
    void logDatabaseFailureIsNotSwallowed() {
        when(accountBalanceMapper.debit(6L, BigDecimal.ONE)).thenReturn(1);
        when(accountBalanceMapper.selectForUpdate(6L)).thenReturn(account("99.00"));
        when(accountBalanceLogMapper.insert(any(AccountBalanceLog.class)))
                .thenThrow(new IllegalStateException("ledger database failure"));

        assertThrows(IllegalStateException.class, () -> accountService.deduct(6L, BigDecimal.ONE));
    }

    @Test
    void balanceLogPaginationUsesStableOffsetAndRejectsInvalidBounds() {
        when(accountBalanceLogMapper.countByUserId(6L)).thenReturn(21L);
        when(accountBalanceLogMapper.selectPageByUserId(6L, 10L, 10L)).thenReturn(List.of());

        var page = accountService.getBalanceLogs(6L, 2, 10);
        assertEquals(21L, page.getTotal());
        assertEquals(3L, page.getPages());
        assertEquals(2L, page.getCurrent());
        assertEquals(10L, page.getSize());
        for (long[] bounds : new long[][]{{0, 10}, {1, 0}, {1, 101}, {Long.MAX_VALUE, 100}}) {
            ConstraintViolationException error = assertThrows(ConstraintViolationException.class,
                    () -> accountService.getBalanceLogs(6L, bounds[0], bounds[1]));
            assertEquals("请求参数错误", error.getMessage());
        }
    }

    private static AccountBalance account(String balance) {
        AccountBalance account = new AccountBalance();
        account.setUserId(6L);
        account.setBalance(new BigDecimal(balance));
        return account;
    }

    @Test
    void batchBalancesUseOneQueryPreserveOrderAndFillUnopenedAccounts() {
        AccountBalance existing = account("12.34");
        when(accountBalanceMapper.selectBalancesByUserIds(List.of(7L, 6L, 8L))).thenReturn(List.of(existing));

        List<AccountBalance> accounts = accountService.getAccounts(List.of(7L, 6L, 7L, 8L));

        assertEquals(List.of(7L, 6L, 8L), accounts.stream().map(AccountBalance::getUserId).toList());
        assertEquals(BigDecimal.ZERO, accounts.get(0).getBalance());
        assertNull(accounts.get(0).getUpdateTime());
        assertEquals(new BigDecimal("12.34"), accounts.get(1).getBalance());
        verify(accountBalanceMapper).selectBalancesByUserIds(List.of(7L, 6L, 8L));
        verifyNoMoreInteractions(accountBalanceMapper);
        verifyNoInteractions(accountBalanceLogMapper);
    }

    @Test
    void batchBalancesRejectEmptyNonPositiveAndTooManyIdsBeforeQuery() {
        for (List<Long> ids : List.of(List.<Long>of(), List.of(0L), List.of(-1L),
                java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList())) {
            assertThrows(ConstraintViolationException.class, () -> accountService.getAccounts(ids));
        }
        verifyNoInteractions(accountBalanceMapper, accountBalanceLogMapper);
    }
}
