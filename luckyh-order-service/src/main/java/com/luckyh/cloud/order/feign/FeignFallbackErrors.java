package com.luckyh.cloud.order.feign;

import com.luckyh.cloud.common.core.domain.Result;
import feign.FeignException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

/** 订单下游降级时，统一识别明确的超时原因。 */
final class FeignFallbackErrors {

    private FeignFallbackErrors() {
    }

    // 逻辑变动: Feign明确超时返回504并隐藏技术异常-20261002-2117-02
    static <T> Result<T> response(Throwable cause, String service) {
        for (Throwable failure = cause; failure != null; failure = failure.getCause()) {
            if (failure instanceof SocketTimeoutException
                    || failure instanceof HttpTimeoutException
                    || failure instanceof TimeoutException
                    || failure instanceof FeignException feignException && feignException.status() == 504) {
                return Result.error(504, service + "请求超时");
            }
            // 逻辑变动: 下游HTTP内部错误保持500且不回显技术信息-20261002-2120-02
            if (failure instanceof FeignException feignException
                    && feignException.status() >= 500
                    && feignException.status() != 503) {
                return Result.error(500, service + "请求失败");
            }
        }
        return Result.error(503, service + "暂不可用");
    }
}
