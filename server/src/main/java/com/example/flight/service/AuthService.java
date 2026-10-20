package com.example.flight.service;

import com.example.flight.model.UserAccount;
import com.example.flight.repository.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录 / 注册业务。
 *
 * 设计要点：
 * 1. 密码只以 PBKDF2 加盐哈希入库，任何响应都不包含 password_hash；
 * 2. 登录成功签发随机 token（内存保存，2 小时过期），前端用 Authorization: Bearer 携带；
 * 3. 同一账号 5 分钟内失败 5 次即短暂锁定（429），记录来自 login_logs，重启不丢。
 */
@Service
public class AuthService {

    private static final long TOKEN_TTL_MS = 2 * 60 * 60 * 1000L;
    private static final int MAX_FAILURES = 5;
    private static final int FAIL_WINDOW_MINUTES = 5;
    private static final int USERNAME_MIN = 3;
    private static final int USERNAME_MAX = 20;
    private static final int PASSWORD_MIN = 6;
    private static final int PASSWORD_MAX = 64;

    private final UserRepository repo;

    /** token -> 会话（内存态，服务重启后需重新登录） */
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public AuthService(UserRepository repo) {
        this.repo = repo;
    }

    // ------------------------------------------------------------------ 注册
    public Map<String, Object> register(String username, String password, String displayName, String ip) {
        String name = normalize(username);
        validate(name, password);
        if (repo.findByUsername(name) != null) {
            throw new IllegalArgumentException("用户名已被注册");
        }
        int id;
        try {
            id = repo.insert(name, PasswordHasher.hash(password), displayName);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("用户名已被注册");
        }
        repo.logLogin(name, id, ip, true, "注册成功");
        UserAccount user = repo.findById(id);
        if (user == null) {
            throw new IllegalStateException("注册成功但读取用户失败，请重试");
        }
        return sessionResponse(user, "注册成功，已自动登录");
    }

    // ------------------------------------------------------------------ 登录
    public Map<String, Object> login(String username, String password, String ip) {
        String name = normalize(username);
        if (name.isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("请输入用户名和密码");
        }
        if (repo.countRecentFailures(name, FAIL_WINDOW_MINUTES) >= MAX_FAILURES) {
            throw new AuthFailedException("该账号密码错误次数过多，请 " + FAIL_WINDOW_MINUTES + " 分钟后再试", 429);
        }
        UserAccount user = repo.findByUsername(name);
        if (user == null) {
            repo.logLogin(name, null, ip, false, "用户不存在");
            throw new AuthFailedException("用户名或密码错误", 401);
        }
        if (!PasswordHasher.matches(password, user.getPasswordHash())) {
            repo.logLogin(name, user.getId(), ip, false, "密码错误");
            throw new AuthFailedException("用户名或密码错误", 401);
        }
        repo.touchLogin(user.getId());
        repo.logLogin(name, user.getId(), ip, true, "登录成功");
        UserAccount fresh = repo.findById(user.getId());
        return sessionResponse(fresh == null ? user : fresh, "登录成功");
    }

    // -------------------------------------------------------------- 校验 token
    public Map<String, Object> me(String token) {
        Session session = requireSession(token);
        UserAccount user = repo.findById(session.userId);
        if (user == null) {
            sessions.remove(token.trim());
            throw new AuthFailedException("账号不存在，请重新登录", 401);
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", 0);
        resp.put("user", publicUser(user));
        return resp;
    }

    public void logout(String token) {
        if (token != null) {
            sessions.remove(token.trim());
        }
    }

    // ------------------------------------------------------------------ 内部
    private Map<String, Object> sessionResponse(UserAccount user, String message) {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new Session(user.getId(), System.currentTimeMillis() + TOKEN_TTL_MS));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", 0);
        resp.put("message", message);
        resp.put("token", token);
        resp.put("expiresIn", TOKEN_TTL_MS / 1000);
        resp.put("user", publicUser(user));
        return resp;
    }

    private Session requireSession(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new AuthFailedException("未登录，请先登录", 401);
        }
        Session session = sessions.get(token.trim());
        if (session == null || session.expiresAt < System.currentTimeMillis()) {
            sessions.remove(token.trim());
            throw new AuthFailedException("登录已过期，请重新登录", 401);
        }
        return session;
    }

    /** 对外可见的用户信息：刻意不含 passwordHash */
    private static Map<String, Object> publicUser(UserAccount user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", user.getId());
        m.put("username", user.getUsername());
        m.put("displayName", user.getDisplayName() == null || user.getDisplayName().isEmpty()
                ? user.getUsername() : user.getDisplayName());
        m.put("loginCount", user.getLoginCount());
        m.put("lastLoginAt", user.getLastLoginAt());
        m.put("createdAt", user.getCreatedAt());
        return m;
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim();
    }

    private static void validate(String username, String password) {
        if (username.length() < USERNAME_MIN || username.length() > USERNAME_MAX) {
            throw new IllegalArgumentException("用户名长度需为 " + USERNAME_MIN + "-" + USERNAME_MAX + " 位");
        }
        if (!username.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("用户名只能包含字母、数字和下划线");
        }
        if (password == null || password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw new IllegalArgumentException("密码长度需为 " + PASSWORD_MIN + "-" + PASSWORD_MAX + " 位");
        }
    }

    private static final class Session {
        private final int userId;
        private final long expiresAt;

        private Session(int userId, long expiresAt) {
            this.userId = userId;
            this.expiresAt = expiresAt;
        }
    }
}
