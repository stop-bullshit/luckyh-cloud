package com.luckyh.cloud.account.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.luckyh.cloud.account.entity.AccountBalanceLog;
import com.luckyh.cloud.account.entity.AccountBalance;
import com.luckyh.cloud.account.mapper.AccountBalanceMapper;
import com.luckyh.cloud.account.mapper.AccountBalanceLogMapper;
import com.luckyh.cloud.account.service.AccountService;
import com.luckyh.cloud.common.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 账户充值接口参数和业务结果测试。
 *
 * @author heng.wang
 * @since 2026-10-02
 */
class AccountControllerTest {

    private AccountService accountService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AccountController(accountService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void rechargeAcceptsValidAmount() throws Exception {
        when(accountService.recharge(6L, new BigDecimal("100.50"))).thenReturn(true);

        mockMvc.perform(post("/accounts/6/recharge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(accountService).recharge(6L, new BigDecimal("100.50"));
    }

    @Test
    void rechargeRejectsInvalidAmountsBeforeCallingService() throws Exception {
        for (String amount : new String[]{"null", "0", "-1", "1.001", "10000000000000000"}) {
            mockMvc.perform(post("/accounts/6/recharge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":" + amount + "}"))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(accountService);
    }

    @Test
    void rechargeReturnsConflictWhenAccountCannotBeUpdated() throws Exception {
        when(accountService.recharge(6L, BigDecimal.ONE)).thenReturn(false);

        mockMvc.perform(post("/accounts/6/recharge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void deductAcceptsValidAmount() throws Exception {
        when(accountService.deduct(6L, new BigDecimal("20.50"))).thenReturn(true);

        mockMvc.perform(post("/accounts/6/deduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":20.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(accountService).deduct(6L, new BigDecimal("20.50"));
    }

    @Test
    void deductReturnsConflictWhenBalanceIsInsufficient() throws Exception {
        when(accountService.deduct(6L, BigDecimal.ONE)).thenReturn(false);

        mockMvc.perform(post("/accounts/6/deduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void balanceLogsKeepExactAmountsAndPaginationFields() throws Exception {
        AccountBalanceLog entry = new AccountBalanceLog();
        entry.setId(7L);
        entry.setUserId(6L);
        entry.setChangeType("DEBIT");
        entry.setChangeAmount(new BigDecimal("-0.01"));
        entry.setBeforeBalance(new BigDecimal("9999999999999999.99"));
        entry.setAfterBalance(new BigDecimal("9999999999999999.98"));
        entry.setOrderNo("ORDER_6");
        entry.setTransactionId("test-xid");
        entry.setCreateTime(LocalDateTime.of(2026, 10, 4, 12, 0, 0));
        Page<AccountBalanceLog> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(entry));
        when(accountService.getBalanceLogs(6L, 1, 10)).thenReturn(page);

        mockMvc.perform(get("/accounts/6/balance-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.pages").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(7))
                .andExpect(jsonPath("$.data.records[0].userId").value(6))
                .andExpect(jsonPath("$.data.records[0].changeAmount").value("-0.01"))
                .andExpect(jsonPath("$.data.records[0].beforeBalance").value("9999999999999999.99"))
                .andExpect(jsonPath("$.data.records[0].afterBalance").value("9999999999999999.98"))
                .andExpect(jsonPath("$.data.records[0].orderNo").value("ORDER_6"))
                .andExpect(jsonPath("$.data.records[0].transactionId").value("test-xid"))
                .andExpect(jsonPath("$.data.records[0].createTime").value("2026-10-04T12:00:00"))
                .andExpect(jsonPath("$.data.records[0].status").doesNotExist());
    }

    @Test
    void invalidBalanceLogPageReturnsHttp400() throws Exception {
        AccountService realService = new AccountService(mock(AccountBalanceMapper.class),
                mock(AccountBalanceLogMapper.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AccountController(realService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        for (String query : new String[]{"current=0", "size=0", "size=101", "current=abc"}) {
            mvc.perform(get("/accounts/6/balance-logs?" + query))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("请求参数错误"));
        }
    }

    @Test
    void debitAcceptsBothLegacyJsonAndOptionalOrderNumber() throws Exception {
        when(accountService.debit(6L, BigDecimal.ONE, null)).thenReturn(true);
        when(accountService.debit(6L, BigDecimal.ONE, "ORDER_6")).thenReturn(true);

        mockMvc.perform(post("/accounts/debit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":6,\"amount\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/accounts/debit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":6,\"amount\":1,\"orderNo\":\"ORDER_6\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(accountService).debit(6L, BigDecimal.ONE, null);
        verify(accountService).debit(6L, BigDecimal.ONE, "ORDER_6");
    }

    @Test
    void creditPassesOptionalOrderNumberToRefund() throws Exception {
        when(accountService.credit(6L, BigDecimal.ONE, "ORDER_6")).thenReturn(true);

        mockMvc.perform(post("/accounts/credit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":6,\"amount\":1,\"orderNo\":\"ORDER_6\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(accountService).credit(6L, BigDecimal.ONE, "ORDER_6");
    }

    @Test
    void batchEndpointUsesOneQueryAndRejectsBadIds() throws Exception {
        AccountBalanceMapper balances = mock(AccountBalanceMapper.class);
        AccountBalanceLogMapper logs = mock(AccountBalanceLogMapper.class);
        MockMvc batchMvc = MockMvcBuilders.standaloneSetup(new AccountController(new AccountService(balances, logs)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        AccountBalance existing = new AccountBalance();
        existing.setUserId(6L);
        existing.setBalance(new BigDecimal("100.50"));
        when(balances.selectBalancesByUserIds(List.of(6L, 7L))).thenReturn(List.of(existing));

        batchMvc.perform(get("/accounts/batch").param("ids", "6,7,6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].userId").value(6))
                .andExpect(jsonPath("$.data[0].balance").value(100.50))
                .andExpect(jsonPath("$.data[1].userId").value(7))
                .andExpect(jsonPath("$.data[1].balance").value(0))
                .andExpect(jsonPath("$.data[1].updateTime").doesNotExist());
        batchMvc.perform(get("/accounts/batch")).andExpect(status().isBadRequest());
        for (String ids : new String[]{"", "abc", "1,,2", "0", "-1", "9223372036854775808"}) {
            batchMvc.perform(get("/accounts/batch").param("ids", ids))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
        verify(balances).selectBalancesByUserIds(List.of(6L, 7L));
        verifyNoMoreInteractions(balances);
        verifyNoInteractions(logs);
    }
}
