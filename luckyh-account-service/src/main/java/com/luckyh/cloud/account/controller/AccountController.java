package com.luckyh.cloud.account.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.luckyh.cloud.account.dto.DebitAccountRequest;
import com.luckyh.cloud.account.dto.DeductAccountRequest;
import com.luckyh.cloud.account.dto.CreditAccountRequest;
import com.luckyh.cloud.account.dto.RechargeAccountRequest;
import com.luckyh.cloud.account.entity.AccountBalance;
import com.luckyh.cloud.account.entity.AccountBalanceLog;
import com.luckyh.cloud.account.service.AccountService;
import com.luckyh.cloud.common.core.domain.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 账户余额查询、充值和事务扣款入口。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    /** 账户余额业务。 */
    private final AccountService accountService;

    /**
     * 批量读取当前登录用户页的账户余额。
     *
     * @param ids 登录用户 ID，逗号分隔，最多 100 个不同 ID
     * @return 去重保序的余额列表，未开户用户余额为零
     */
    @GetMapping("/batch")
    public Result<List<AccountBalance>> getAccounts(@RequestParam List<Long> ids) {
        // 逻辑变动: 余额列表批量读取避免逐行查询-20261004-1209-01
        return Result.success(accountService.getAccounts(ids));
    }

    /**
     * 查询业务用户账户；尚未产生余额记录时按零余额返回。
     *
     * @param userId 账户所属业务用户 ID
     * @return 账户余额查询结果
     */
    @GetMapping("/{userId}")
    public Result<AccountBalance> getAccount(@PathVariable Long userId) {
        return Result.success(accountService.getAccount(userId));
    }

    /**
     * 查看当前用户的余额变动明细。
     *
     * @param userId 账户所属用户 ID
     * @param current 当前页
     * @param size 每页条数
     * @return 最近变动在前的余额明细分页
     */
    @GetMapping("/{userId}/balance-logs")
    public Result<Page<AccountBalanceLog>> getBalanceLogs(@PathVariable Long userId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(accountService.getBalanceLogs(userId, current, size));
    }

    /**
     * 为已有业务用户账户增加余额。
     *
     * @param userId 账户所属业务用户 ID
     * @param request 充值请求
     * @return 充值结果
     */
    @PostMapping("/{userId}/recharge")
    public Result<Void> recharge(@PathVariable Long userId,
                                 @Valid @RequestBody RechargeAccountRequest request) {
        // 逻辑变动: 账户余额增量充值-20261002-1556-01
        if (!accountService.recharge(userId, request.getAmount())) {
            return Result.error(409, "账户不存在或充值后余额超过上限");
        }
        return Result.success();
    }

    /**
     * 从业务用户账户中扣减指定余额。
     *
     * @param userId 账户所属业务用户 ID
     * @param request 扣减请求
     * @return 扣减结果
     */
    @PostMapping("/{userId}/deduct")
    public Result<Void> deduct(@PathVariable Long userId,
                               @Valid @RequestBody DeductAccountRequest request) {
        // 逻辑变动: 管理端余额扣减-20261002-1645-01
        if (!accountService.deduct(userId, request.getAmount())) {
            return Result.error(409, "账户不存在或余额不足");
        }
        return Result.success();
    }

    /**
     * 执行全局事务分支扣款，账户不存在或余额不足时返回业务状态码 409。
     *
     * @param request 账户扣款请求
     * @return 扣款结果
     */
    @PostMapping("/debit")
    public Result<Void> debit(@Valid @RequestBody DebitAccountRequest request) {
        if (!accountService.debit(request.getUserId(), request.getAmount(), request.getOrderNo())) {
            return Result.error(409, "账户不存在或余额不足");
        }
        return Result.success();
    }

    /** 执行订单退款的全局事务余额分支。 */
    @PostMapping("/credit")
    public Result<Void> credit(@Valid @RequestBody CreditAccountRequest request) {
        if (!accountService.credit(request.getUserId(), request.getAmount(), request.getOrderNo())) {
            return Result.error(409, "账户不存在或退款后余额超过上限");
        }
        return Result.success();
    }
}
