package com.luckyh.cloud.common.core.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 统一响应结果的序列化和消息语义检查。
 *
 * @author Lucky
 * @since 2026-10-02
 */
class ResultTest {

    @Test
    void preservesResponseShapeAndMessages() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        JsonNode defaultSuccess = mapper.readTree(mapper.writeValueAsString(Result.success("text")));
        assertEquals(3, defaultSuccess.size());
        assertEquals(200, defaultSuccess.get("code").asInt());
        assertEquals("success", defaultSuccess.get("message").asText());
        assertEquals("text", defaultSuccess.get("data").asText());

        Result<Integer> explicitSuccess = Result.success("登录成功", 42);
        assertEquals(200, explicitSuccess.getCode());
        assertEquals("登录成功", explicitSuccess.getMessage());
        assertEquals(42, explicitSuccess.getData());

        Result<Void> unauthorized = Result.unauthorized("令牌无效");
        assertEquals(401, unauthorized.getCode());
        assertEquals("令牌无效", unauthorized.getMessage());
        assertNull(unauthorized.getData());

        JsonNode loginSuccess = mapper.readTree(mapper.writeValueAsString(R.success("登录成功", 42)));
        assertEquals(5, loginSuccess.size());
        assertEquals("登录成功", loginSuccess.get("message").asText());
        assertEquals(true, loginSuccess.get("success").asBoolean());
        assertEquals(false, loginSuccess.get("fail").asBoolean());

        JsonNode loginFailure = mapper.readTree(mapper.writeValueAsString(R.unauthorized("令牌无效")));
        assertEquals(401, loginFailure.get("code").asInt());
        assertEquals(false, loginFailure.get("success").asBoolean());
        assertEquals(true, loginFailure.get("fail").asBoolean());
    }
}
