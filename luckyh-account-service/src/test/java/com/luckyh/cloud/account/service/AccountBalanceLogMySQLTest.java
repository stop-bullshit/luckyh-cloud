package com.luckyh.cloud.account.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luckyh.cloud.account.entity.AccountBalanceLog;
import com.luckyh.cloud.account.mapper.AccountBalanceLogMapper;
import com.luckyh.cloud.account.mapper.AccountBalanceMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 仅在显式授权的唯一隔离 MySQL 库验证真实 Mapper 和本地事务.
 *
 * @author heng.wang
 * @since 2026-10-04
 */
@EnabledIfEnvironmentVariable(named = "ALLOW_ISOLATED_INTEGRATION", matches = "1")
class AccountBalanceLogMySQLTest {

    private static AnnotationConfigApplicationContext application;
    private static JdbcTemplate jdbc;
    private static AccountService service;
    private static final List<Long> createdUserIds = new ArrayList<>();

    @BeforeAll
    static void openIsolatedDatabase() {
        String url = System.getenv("ISOLATED_ACCOUNT_JDBC_URL");
        assumeTrue("1".equals(System.getenv("ALLOW_ISOLATED_INTEGRATION")) && url != null && !url.isBlank(),
                "需显式启用唯一隔离 MySQL 测试库");
        if (!url.startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("隔离测试只支持 MySQL JDBC URL");
        }
        String database = URI.create(url.substring(5)).getPath();
        if (database == null || !database.matches("/luckyh_go_test_[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("拒绝连接非 luckyh_go_test_* 隔离库");
        }
        application = new AnnotationConfigApplicationContext(IsolatedConfiguration.class);
        jdbc = new JdbcTemplate(application.getBean(DataSource.class));
        service = application.getBean(AccountService.class);
    }

    @AfterAll
    static void closeIsolatedDatabase() {
        if (application != null) {
            try {
                for (long userId : createdUserIds) {
                    jdbc.update("DELETE FROM account_balance_log WHERE user_id = ?", userId);
                    jdbc.update("DELETE FROM account_balance WHERE user_id = ?", userId);
                }
            } finally {
                application.close();
            }
        }
    }

    @Test
    void realRechargeAndDeductPersistAccurateBalanceChain() {
        long userId = newUserId();
        service.recharge(userId, new BigDecimal("100.50"));
        service.deduct(userId, new BigDecimal("20.25"));

        assertEquals(new BigDecimal("80.25"), balance(userId));
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT change_type, change_amount, before_balance, after_balance
                FROM account_balance_log WHERE user_id = ? ORDER BY id
                """, userId);
        assertEquals(2, rows.size());
        assertEquals("RECHARGE", rows.get(0).get("change_type"));
        assertEquals(new BigDecimal("0.00"), rows.get(0).get("before_balance"));
        assertEquals(new BigDecimal("100.50"), rows.get(0).get("after_balance"));
        assertEquals("DEDUCT", rows.get(1).get("change_type"));
        assertEquals(new BigDecimal("-20.25"), rows.get(1).get("change_amount"));
        assertEquals(new BigDecimal("100.50"), rows.get(1).get("before_balance"));
        assertEquals(new BigDecimal("80.25"), rows.get(1).get("after_balance"));

        var page = service.getBalanceLogs(userId, 1, 1);
        assertEquals(2L, page.getTotal());
        assertEquals(2L, page.getPages());
        assertEquals("DEDUCT", page.getRecords().get(0).getChangeType());
        JsonNode json = new ObjectMapper().findAndRegisterModules().valueToTree(page);
        JsonNode latest = json.get("records").get(0);
        assertTrue(latest.get("changeAmount").isTextual());
        assertEquals("-20.25", latest.get("changeAmount").asText());
        assertEquals("100.50", latest.get("beforeBalance").asText());
        assertEquals("80.25", latest.get("afterBalance").asText());
    }

    @Test
    void realOverflowAndInsufficientBalanceDoNotInventLogs() {
        long userId = newUserId();
        jdbc.update("INSERT INTO account_balance(user_id,balance) VALUES (?,?)", userId,
                new BigDecimal("9999999999999999.99"));
        service.recharge(userId, new BigDecimal("0.01"));
        assertEquals(new BigDecimal("9999999999999999.99"), balance(userId));
        assertEquals(0L, logCount(userId));
        long missingUserId = newUserId();
        assertFalse(service.deduct(missingUserId, BigDecimal.ONE));
        assertEquals(0L, logCount(missingUserId));
    }

    @Test
    void realBalanceUpdateRollsBackWhenLogPersistenceFails() {
        long userId = newUserId();
        jdbc.update("INSERT INTO account_balance(user_id,balance) VALUES (?,100.00)", userId);
        AccountBalanceLogMapper failingLogs = mock(AccountBalanceLogMapper.class);
        when(failingLogs.insert(any(AccountBalanceLog.class)))
                .thenThrow(new IllegalStateException("isolated ledger write failure"));
        ProxyFactory factory = new ProxyFactory(new AccountService(
                application.getBean(AccountBalanceMapper.class), failingLogs));
        factory.addAdvice(new TransactionInterceptor(application.getBean(PlatformTransactionManager.class),
                new AnnotationTransactionAttributeSource()));
        AccountService failingService = (AccountService) factory.getProxy();

        assertThrows(IllegalStateException.class, () -> failingService.deduct(userId, BigDecimal.ONE));
        assertEquals(new BigDecimal("100.00"), balance(userId));
        assertEquals(0L, logCount(userId));
    }

    private static BigDecimal balance(long userId) {
        return jdbc.queryForObject("SELECT balance FROM account_balance WHERE user_id = ?", BigDecimal.class, userId);
    }

    private static long logCount(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM account_balance_log WHERE user_id = ?", Long.class, userId);
    }

    private static long newUserId() {
        while (true) {
            long userId = ThreadLocalRandom.current().nextLong(1, 1L << 52);
            long accountCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM account_balance WHERE user_id = ?", Long.class, userId);
            if (accountCount == 0 && logCount(userId) == 0 && !createdUserIds.contains(userId)) {
                createdUserIds.add(userId);
                return userId;
            }
        }
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = AccountBalanceMapper.class)
    static class IsolatedConfiguration {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource datasource = new DriverManagerDataSource();
            datasource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            datasource.setUrl(System.getenv("ISOLATED_ACCOUNT_JDBC_URL"));
            datasource.setUsername(System.getenv("ISOLATED_ACCOUNT_JDBC_USER"));
            datasource.setPassword(System.getenv("ISOLATED_ACCOUNT_JDBC_PASSWORD"));
            return datasource;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource datasource) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(datasource);
            factory.setConfiguration(configuration);
            return factory.getObject();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource datasource) {
            return new DataSourceTransactionManager(datasource);
        }

        @Bean
        AccountService accountService(AccountBalanceMapper balances, AccountBalanceLogMapper logs) {
            return new AccountService(balances, logs);
        }
    }
}
