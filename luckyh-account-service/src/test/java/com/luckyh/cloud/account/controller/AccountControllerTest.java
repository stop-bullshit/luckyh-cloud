package com.luckyh.cloud.account.controller;

import com.luckyh.cloud.account.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        mockMvc = MockMvcBuilders.standaloneSetup(new AccountController(accountService)).build();
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
}
