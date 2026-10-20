-- =============================================================
--  登录模块 —— 用户账号表 + 登录日志表
--  与 schema.sql 中的航班表同库（flight_db），可重复执行
--  用法：mysql -uroot -p123456 < db/auth.sql
--  说明：密码不以明文存储，保存的是 PBKDF2-HMAC-SHA256 加盐哈希，
--        格式 pbkdf2_sha256$迭代次数$盐(Base64)$摘要(Base64)
-- =============================================================

USE flight_db;

-- -------------------------------------------------------------
-- 1. 用户账号表
--    collate utf8mb4_0900_ai_ci 让唯一索引对大小写不敏感，
--    即 Admin 与 admin 视为同一个账号
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id            INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(30)  NOT NULL                COMMENT '登录账号，3-20 位字母数字下划线',
    password_hash VARCHAR(255) NOT NULL                COMMENT 'PBKDF2 加盐哈希，不存明文',
    display_name  VARCHAR(50)  NULL                    COMMENT '显示昵称（可选）',
    login_count   INT UNSIGNED NOT NULL DEFAULT 0      COMMENT '累计成功登录次数',
    last_login_at DATETIME     NULL                    COMMENT '最近一次登录时间',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '系统用户账号（登录使用）';

-- -------------------------------------------------------------
-- 2. 登录日志表
--    成功和失败都记，用于审计；失败记录同时服务于
--    「5 分钟内连续失败 5 次锁定」的简单防爆破逻辑
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS login_logs (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    INT UNSIGNED    NULL                    COMMENT '→ users.id，账号不存在时为 NULL',
    username   VARCHAR(30)     NOT NULL                COMMENT '本次提交的账号',
    ip         VARCHAR(45)     NULL                    COMMENT '来源 IP（兼容 IPv6）',
    success    TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '1 成功 / 0 失败',
    message    VARCHAR(100)    NULL                    COMMENT '结果说明：登录成功/密码错误/用户不存在…',
    created_at TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_username_time (username, created_at),
    KEY idx_created (created_at),
    CONSTRAINT fk_log_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '登录日志';

-- -------------------------------------------------------------
-- 3. 演示账号：demo / test1234
--    哈希由 PBKDF2-HMAC-SHA256（10 万次迭代、16 字节随机盐）生成，
--    与后端 PasswordHasher 的实现完全一致，可直接登录测试。
--    已存在则跳过，脚本可重复执行。
-- -------------------------------------------------------------
INSERT INTO users (username, password_hash, display_name)
SELECT 'demo',
       'pbkdf2_sha256$100000$1LYUICcQOngBiKtYrYZDJA==$1fpHrRMi6c9Bgj/wOoYlh6aA/iz1numvFu2TBUDfNfY=',
       '演示用户'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'demo');
