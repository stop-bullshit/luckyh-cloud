package com.luckyh.cloud.auth.controller;

import com.luckyh.cloud.auth.dto.LoginDTO;
import com.luckyh.cloud.auth.service.AuthService;
import com.luckyh.cloud.common.core.exception.BusinessException;
import com.luckyh.cloud.common.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    @Test
    void loginBusinessErrorKeepsMessage() throws Exception {
        AuthService authService = mock(AuthService.class);
        when(authService.login(any(LoginDTO.class)))
                .thenThrow(new BusinessException(401, "用户名或密码错误"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void loginUnexpectedErrorDoesNotExposeInternalDetails() throws Exception {
        AuthService authService = mock(AuthService.class);
        when(authService.login(any(LoginDTO.class)))
                .thenThrow(new IllegalStateException("database password leaked"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("服务处理失败，请稍后重试"));
    }
}
