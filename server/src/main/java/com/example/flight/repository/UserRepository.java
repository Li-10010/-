package com.example.flight.repository;

import com.example.flight.model.UserAccount;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;

/** 用户账号与登录日志的数据访问层（JdbcTemplate，全部 ? 占位符绑定） */
@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String BASE_SELECT =
            "SELECT id, username, password_hash, display_name, login_count, " +
            "       DATE_FORMAT(last_login_at, '%Y-%m-%d %H:%i:%s') AS last_login_at, " +
            "       DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s')    AS created_at " +
            "FROM users ";

    private static final RowMapper<UserAccount> MAPPER = (rs, i) -> {
        UserAccount u = new UserAccount();
        u.setId(rs.getInt("id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setDisplayName(rs.getString("display_name"));
        u.setLoginCount(rs.getInt("login_count"));
        u.setLastLoginAt(rs.getString("last_login_at"));
        u.setCreatedAt(rs.getString("created_at"));
        return u;
    };

    public UserAccount findByUsername(String username) {
        try {
            return jdbc.queryForObject(BASE_SELECT + "WHERE username = ?", MAPPER, username);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public UserAccount findById(int id) {
        try {
            return jdbc.queryForObject(BASE_SELECT + "WHERE id = ?", MAPPER, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** 新增用户，返回自增主键；用户名重复会抛 DuplicateKeyException */
    public int insert(String username, String passwordHash, String displayName) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO users (username, password_hash, display_name) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            if (displayName == null || displayName.trim().isEmpty()) {
                ps.setNull(3, Types.VARCHAR);
            } else {
                ps.setString(3, displayName.trim());
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0 : key.intValue();
    }

    /** 登录成功后累加次数并刷新最近登录时间 */
    public void touchLogin(int id) {
        jdbc.update("UPDATE users SET login_count = login_count + 1, last_login_at = NOW() WHERE id = ?", id);
    }

    /** 最近 N 分钟内的失败次数（用于简单防爆破） */
    public int countRecentFailures(String username, int minutes) {
        Timestamp cutoff = new Timestamp(System.currentTimeMillis() - minutes * 60_000L);
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM login_logs WHERE username = ? AND success = 0 AND created_at > ?",
                Integer.class, username, cutoff);
        return n == null ? 0 : n;
    }

    /** 写一条登录日志（成功、失败都记） */
    public void logLogin(String username, Integer userId, String ip, boolean success, String message) {
        jdbc.update("INSERT INTO login_logs (user_id, username, ip, success, message) VALUES (?, ?, ?, ?, ?)",
                userId, username, ip, success ? 1 : 0, message);
    }

    public int countUsers() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        return n == null ? 0 : n;
    }
}
