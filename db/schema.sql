-- =============================================================
--  航班查询系统 —— MySQL 数据库结构 (MySQL 8.0+)
--  字符集：utf8mb4 / utf8mb4_0900_ai_ci
--  时间约定：所有航班时刻均为【机场当地时间】，本系统全部机场位于
--            中国境内，统一时区 Asia/Shanghai (UTC+8)。
--            存储类型 DATETIME，格式 'YYYY-MM-DD HH:MM:SS'（24 小时制）。
--            跨零点航班：arr_time 的日期会自动顺延到次日。
-- =============================================================

DROP DATABASE IF EXISTS flight_db;
CREATE DATABASE flight_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE flight_db;

-- -------------------------------------------------------------
-- 1. 航空公司表
-- -------------------------------------------------------------
CREATE TABLE airlines (
    id           SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    airline_code CHAR(2)           NOT NULL                COMMENT '航司 IATA 二字码，如 CA/MU',
    airline_name VARCHAR(50)       NOT NULL                COMMENT '航司全称',
    PRIMARY KEY (id),
    UNIQUE KEY uk_airline_code (airline_code)
) ENGINE = InnoDB COMMENT = '航空公司';

-- -------------------------------------------------------------
-- 2. 机场表
-- -------------------------------------------------------------
CREATE TABLE airports (
    id           SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    iata_code    CHAR(3)           NOT NULL                COMMENT '机场 IATA 三字码，如 PEK',
    airport_name VARCHAR(60)       NOT NULL                COMMENT '机场全称',
    city         VARCHAR(30)       NOT NULL                COMMENT '所在城市',
    timezone     VARCHAR(40)       NOT NULL DEFAULT 'Asia/Shanghai'
                                                           COMMENT '时区，IANA 名称',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iata_code (iata_code),
    KEY idx_city (city)
) ENGINE = InnoDB COMMENT = '机场';

-- -------------------------------------------------------------
-- 3. 航班表（核心表）
--    一条记录 = 某航司某航班号在某一天的一次执行
-- -------------------------------------------------------------
CREATE TABLE flights (
    id             INT UNSIGNED      NOT NULL AUTO_INCREMENT COMMENT '主键',
    airline_id     SMALLINT UNSIGNED NOT NULL                COMMENT '航司 id → airlines.id',
    flight_no      VARCHAR(8)        NOT NULL                COMMENT '航班号，如 CA1832',
    dep_airport_id SMALLINT UNSIGNED NOT NULL                COMMENT '起飞机场 id → airports.id',
    arr_airport_id SMALLINT UNSIGNED NOT NULL                COMMENT '到达机场 id → airports.id',
    flight_date    DATE              NOT NULL                COMMENT '出发日期（当地时间）',
    dep_time       DATETIME          NOT NULL                COMMENT '计划起飞时间（起飞机场当地时间）',
    arr_time       DATETIME          NOT NULL                COMMENT '计划到达时间（到达机场当地时间）',
    duration_min   SMALLINT UNSIGNED NOT NULL                COMMENT '飞行时长（分钟）',
    price          DECIMAL(8, 2)     NOT NULL                COMMENT '票价（人民币元）',
    discount_tag   VARCHAR(10)       NULL                    COMMENT '优惠标签：特惠/折扣，NULL 表示全价',
    status         ENUM('计划','延误','取消') NOT NULL DEFAULT '计划' COMMENT '航班状态',
    created_at     TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_flight_date (flight_no, flight_date, dep_airport_id),
    KEY idx_search (flight_date, dep_airport_id, price),
    KEY idx_arr_date (arr_airport_id, flight_date),
    KEY idx_price (price),
    KEY idx_airline (airline_id),
    CONSTRAINT fk_flight_airline FOREIGN KEY (airline_id)     REFERENCES airlines (id),
    CONSTRAINT fk_flight_dep     FOREIGN KEY (dep_airport_id) REFERENCES airports (id),
    CONSTRAINT fk_flight_arr     FOREIGN KEY (arr_airport_id) REFERENCES airports (id),
    CONSTRAINT ck_price       CHECK (price >= 0),
    CONSTRAINT ck_time_order  CHECK (arr_time > dep_time),
    CONSTRAINT ck_duration    CHECK (duration_min > 0)
) ENGINE = InnoDB COMMENT = '航班计划（含票价）';

-- -------------------------------------------------------------
-- 4. 视图：航班明细（把 3 张表拼成前端可直接用的一行数据）
--    同时给出城市、机场、航司名称和「起降时刻」字符串
-- -------------------------------------------------------------
CREATE OR REPLACE VIEW v_flight_detail AS
SELECT
    f.id,
    a.airline_code                                          AS airline_code,
    a.airline_name                                          AS airline_name,
    f.flight_no,
    dp.city                                                 AS dep_city,
    dp.iata_code                                            AS dep_code,
    dp.airport_name                                         AS dep_airport,
    ap.city                                                 AS arr_city,
    ap.iata_code                                            AS arr_code,
    ap.airport_name                                         AS arr_airport,
    f.flight_date,
    DATE_FORMAT(f.dep_time, '%Y-%m-%d %H:%i')               AS dep_time,
    DATE_FORMAT(f.arr_time, '%Y-%m-%d %H:%i')               AS arr_time,
    TIME_FORMAT(f.dep_time, '%H:%i')                        AS dep_hm,
    TIME_FORMAT(f.arr_time, '%H:%i')                        AS arr_hm,
    f.duration_min,
    CONCAT(FLOOR(f.duration_min / 60), '小时',
           LPAD(f.duration_min % 60, 2, '0'), '分')         AS duration_text,
    f.price,
    f.discount_tag,
    f.status,
    dp.timezone                                             AS dep_timezone,
    ap.timezone                                             AS arr_timezone
FROM flights f
JOIN airlines a  ON a.id  = f.airline_id
JOIN airports dp ON dp.id = f.dep_airport_id
JOIN airports ap ON ap.id = f.arr_airport_id;

-- -------------------------------------------------------------
-- 5. 用户账号表（登录模块）
--    密码不存明文，存 PBKDF2-HMAC-SHA256 加盐哈希：
--    pbkdf2_sha256$迭代次数$盐(Base64)$摘要(Base64)
--    单独建表脚本见 db/auth.sql（可对已有库重复执行）
-- -------------------------------------------------------------
CREATE TABLE users (
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
-- 6. 登录日志表（成功/失败都记；失败记录用于防爆破计数）
-- -------------------------------------------------------------
CREATE TABLE login_logs (
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

-- 演示账号：demo / test1234
INSERT INTO users (username, password_hash, display_name) VALUES
('demo',
 'pbkdf2_sha256$100000$1LYUICcQOngBiKtYrYZDJA==$1fpHrRMi6c9Bgj/wOoYlh6aA/iz1numvFu2TBUDfNfY=',
 '演示用户');
