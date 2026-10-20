package com.example.flight.service;

/**
 * 认证类业务异常：携带 HTTP 状态码，由 AuthController 统一转成 JSON。
 * 400 参数问题 / 401 账号或密码错误、未登录 / 429 失败次数过多
 */
public class AuthFailedException extends RuntimeException {

    private final int status;

    public AuthFailedException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
