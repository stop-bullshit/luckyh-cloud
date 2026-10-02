package com.luckyh.cloud.order.feign;

import com.alibaba.cloud.seata.feign.SeataFeignClientAutoConfiguration;
import com.sun.net.httpserver.HttpServer;
import feign.FeignException;
import feign.RequestInterceptor;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.seata.core.context.RootContext;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;

import static org.junit.jupiter.api.Assertions.*;

class FeignXidIntegrationTest {

    @Test
    void actualCircuitBreakerFeignPreservesXidAndGivesFactoryTheHttpCause() throws Exception {
        AtomicBoolean failing = new AtomicBoolean();
        AtomicReference<List<String>> receivedXids = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/probe", exchange -> {
            receivedXids.set(exchange.getRequestHeaders().get(RootContext.KEY_XID));
            byte[] body = "probe-response".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(failing.get() ? 503 : 200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        RootContext.bind("probe-xid");
        try {
            new ApplicationContextRunner()
                    .withUserConfiguration(ProbeConfiguration.class)
                    .withConfiguration(AutoConfigurations.of(FeignAutoConfiguration.class,
                            Resilience4JAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
                            SeataFeignClientAutoConfiguration.class))
                    .withPropertyValues("probe.url=http://127.0.0.1:" + server.getAddress().getPort(),
                            "spring.cloud.openfeign.circuitbreaker.enabled=true",
                            "spring.cloud.circuitbreaker.resilience4j.disable-thread-pool=true",
                            "spring.cloud.circuitbreaker.bulkhead.resilience4j.enabled=false")
                    .run(context -> {
                        assertNull(context.getStartupFailure());
                        ProbeFeign client = context.getBean(ProbeFeign.class);
                        assertEquals("probe-response", client.call());
                        assertEquals(List.of("probe-xid"), receivedXids.get());
                        assertEquals(Thread.currentThread(), context.getBean(AtomicReference.class).get());
                        assertEquals("probe-xid", RootContext.getXID());
                        failing.set(true);
                        assertEquals("fallback", client.call());
                        assertInstanceOf(FeignException.ServiceUnavailable.class,
                                context.getBean(ProbeFallbackFactory.class).cause.get());
                        assertEquals("probe-xid", RootContext.getXID());
                    });
        } finally {
            RootContext.unbind();
            server.stop(0);
        }
    }

    @FeignClient(name = "probe", url = "${probe.url}", fallbackFactory = ProbeFallbackFactory.class)
    interface ProbeFeign {
        @GetMapping("/probe")
        String call();
    }

    static class ProbeFallbackFactory implements FallbackFactory<ProbeFeign> {
        private final AtomicReference<Throwable> cause = new AtomicReference<>();

        @Override
        public ProbeFeign create(Throwable failure) {
            cause.set(failure);
            return () -> "fallback";
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(clients = ProbeFeign.class)
    static class ProbeConfiguration {
        @Bean
        CircuitBreakerRegistry circuitBreakerRegistry() { return CircuitBreakerRegistry.ofDefaults(); }

        @Bean
        TimeLimiterRegistry timeLimiterRegistry() { return TimeLimiterRegistry.ofDefaults(); }

        @Bean
        ProbeFallbackFactory fallbackFactory() { return new ProbeFallbackFactory(); }

        @Bean
        AtomicReference<Thread> interceptedThread() { return new AtomicReference<>(); }

        @Bean
        RequestInterceptor captureThread(AtomicReference<Thread> interceptedThread) {
            return template -> interceptedThread.set(Thread.currentThread());
        }
    }
}
