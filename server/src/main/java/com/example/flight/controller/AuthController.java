package com.example.flight.controller;

import com.example.flight.service.AuthFailedException;
import com.example.flight.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 登录 / 注册接口
 *
 * POST /api/auth/register  {"username":"alice","password":"123456","displayName":"小艾"}
 * POST /api/auth/login     {"username":"alice","password":"123456"}
 * GET  /api/auth/me        Header: Authorization: Bearer {token}
 * POST /api/auth/logout    Header: Authorization: Bearer {token}
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body, HttpServletRequest request) {
        return auth.register(body.get("username"), body.get("password"), body.get("displayName"), clientIp(request));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        return auth.login(body.get("username"), body.get("password"), clientIp(request));
    }

    @GetMapping("/me")
    public Map<String, Object> me(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return auth.me(bearer(authorization));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.logout(bearer(authorization));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", 0);
        resp.put("message", "已退出登录");
        return resp;
    }

    // ------------------------------------------------------------------ 异常
    @ExceptionHandler(AuthFailedException.class)
    public ResponseEntity<Map<String, Object>> onAuthFailed(AuthFailedException e) {
        return error(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> onBadRequest(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST.value(), e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> onServerError(IllegalStateException e) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> error(int status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", status);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    /** 兼容 "Bearer xxx" 与直接传 token 两种写法 */
    private static String bearer(String authorization) {
        if (authorization == null) {
            return null;
        }
        String value = authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return value;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
