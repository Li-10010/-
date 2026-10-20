# 航班查询系统 · 数据库 + Java 后端

## 一、目录结构

```
实验室3/
├── web/
│   ├── login.html          # 登录 / 注册页（账号写入 flight_db.users）
│   └── index.html          # 查询主页面（调用后端接口，后端离线时自动降级为本地演示数据）
├── server/                 # Java 后端（Spring Boot 2.7 + JdbcTemplate）
│   ├── pom.xml
│   ├── start-server.bat    # 一键启动（双击）
│   ├── README.md           # 后端说明 / 接口文档 / 实测记录 / 排错
│   └── src/main/
│       ├── java/com/example/flight/
│       │   ├── FlightApplication.java
│       │   ├── controller/FlightController.java
│       │   ├── controller/AuthController.java      # 登录 / 注册 / 会话接口
│       │   ├── service/FlightService.java
│       │   ├── service/AuthService.java            # 登录业务 + token 会话
│       │   ├── service/PasswordHasher.java         # PBKDF2 加盐哈希
│       │   ├── service/AuthFailedException.java
│       │   ├── repository/FlightRepository.java
│       │   ├── repository/UserRepository.java      # 用户表 + 登录日志
│       │   ├── model/FlightRow.java
│       │   └── model/UserAccount.java
│       └── resources/application.yml
├── db/
│   ├── schema.sql          # 建库 + 建表 + 视图 + 用户表（先执行）
│   ├── auth.sql            # 登录模块两张表（已有库单独执行，可重复跑）
│   ├── seed.sql            # 模拟数据 522 条航班（后执行）
│   └── queries.sql         # 9 组查询示例（可选执行）
├── tools/
│   └── gen_seed.py         # 数据生成脚本（可改参数重新生成 seed.sql）
└── README.md
```

## 1.1、三分钟跑起来

> 本机已完成建库与数据导入（`flight_db`，522 条航班 + 用户表），可直接从第 2 步开始。

```bash
# 1) 建库导数据（重装环境时才需要；会先 DROP 再建）
cd "C:/Users/Y9000P/Desktop/实验室私"
"/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -uroot -p123456 < db/schema.sql
"/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -uroot -p123456 flight_db < db/seed.sql
# 已有库只补登录模块的两张表（可重复执行，不会动航班数据）
"/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -uroot -p123456 < db/auth.sql

# 2) 启动后端（依赖已编译好，直接运行）
cd server
./start-server.bat            # Windows 双击亦可
# 或命令行：
# export JAVA_HOME="D:/coder/jdk1.8.0_201"
# "$JAVA_HOME/bin/java" -jar target/flight-server-1.0.0.jar --server.port=8080

# 3) 浏览器打开（先登录，再查询）
#    http://localhost:8080/          → 未登录会自动跳到登录页
#    http://localhost:8080/login.html → 登录 / 注册
#    演示账号：demo / test1234
```

数据库密码已在 `server/src/main/resources/application.yml` 中默认设为 `123456`，
换环境时用环境变量 `MYSQL_PASSWORD` 覆盖。

接口示例：`http://localhost:8080/api/flights?city=北京&date=2026-10-21&maxPrice=1200&sort=price`

## 1.2、登录模块（账号写进数据库）

### 流程

```
浏览器 login.html
   │  POST /api/auth/register  {username, password}
   │  POST /api/auth/login     {username, password}
   ▼
AuthController → AuthService（校验 + PBKDF2 哈希 + 签发 token）
   ▼
UserRepository → MySQL  flight_db
   ├─ users       账号（用户名唯一、密码哈希、登录次数、最近登录时间）
   └─ login_logs  每次成功/失败都记一行（账号、IP、原因、时间）
   ▼
登录成功 → sessionStorage 存 token → 跳 index.html
   index.html 每次加载用 GET /api/auth/me 验 token，失效则回登录页
```

### 两张表

| 表 | 关键字段 | 说明 |
|---|---|---|
| `users` | `username`（唯一）、`password_hash`、`display_name`、`login_count`、`last_login_at` | 账号主表；唯一索引是 `utf8mb4_0900_ai_ci`，`Alice` 与 `alice` 视为同一账号 |
| `login_logs` | `user_id`、`username`、`ip`、`success`、`message`、`created_at` | 审计用；失败记录同时供「5 分钟内 5 次失败锁定」计数 |

建表脚本：`db/auth.sql`（对已有库可重复执行），也已并入 `db/schema.sql`。

### 接口

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| POST | `/api/auth/register` | `{username, password}` | `{code:0, token, expiresIn, user}`（注册即登录） |
| POST | `/api/auth/login` | `{username, password}` | 同上；失败 401，锁定 429 |
| GET | `/api/auth/me` | Header `Authorization: Bearer {token}` | `{code:0, user}`；无/失效 token 401 |
| POST | `/api/auth/logout` | 同上 | `{code:0, message}` |

校验规则：用户名 3-20 位字母/数字/下划线，密码 6-64 位。

> 注册页只收集**用户名 + 密码**，不再有昵称输入框。新注册用户 `users.display_name` 落库为 `NULL`，
> 接口返回时统一用用户名兜底，所以右上角显示的就是用户名。老账号库里已有昵称的仍会照旧显示。

### 密码怎么存

不存明文。`PasswordHasher` 用 **PBKDF2-HMAC-SHA256，10 万次迭代、每用户 16 字节随机盐**，
入库格式 `pbkdf2_sha256$100000$盐(Base64)$摘要(Base64)`（90 字符），校验用 `MessageDigest.isEqual` 常量时间比较。
任何接口响应都不会返回 `password_hash`。

### 已知边界

- 会话 token 存在服务端内存，**重启后端需重新登录**；要多实例/持久会话可改 JWT 或把 token 落库。
- `/api/flights`、`/api/meta` **未强制鉴权**（保留原有 curl 联调与离线演示能力），登录守卫在页面层。
  需要后端硬拦截时，加一个 `HandlerInterceptor` 注册到 `WebMvcConfigurer` 即可。

## 架构

```
浏览器 (web/index.html)
   │  fetch /api/flights?city=&date=&maxPrice=&airline=&sort=
   ▼
Spring Boot (server, :8080)
   │  Controller → Service → Repository(JdbcTemplate 动态 SQL)
   ▼
MySQL 8.0  flight_db
   ├─ airlines  8 行
   ├─ airports  18 行
   ├─ flights   522 行
   └─ v_flight_detail（三表 JOIN 视图，含时长/时分格式化字段）
```


## 二、时区与时间格式约定

| 项目 | 约定 |
|---|---|
| 时区 | 全部机场位于中国境内，统一 `Asia/Shanghai`（**UTC+8**），`airports.timezone` 字段显式声明 |
| 存储类型 | `DATE` 存出发日期；`DATETIME` 存计划起降时刻 |
| 时间格式 | `YYYY-MM-DD HH:MM:SS`，24 小时制 |
| 语义 | `dep_time` 是**起飞机场当地时间**，`arr_time` 是**到达机场当地时间** |
| 跨零点 | `arr_time` 自动顺延到次日（如 23:40 起飞 + 2h50m → 次日 02:30），共 35 条此类航班 |
| 一致性 | 已加约束 `CHECK (arr_time > dep_time)`，并有 `duration_min` 冗余字段便于校验与排序 |

> 若将来接入国际航线（跨时区），建议改为「UTC 存储 + 时区字段」并在展示层换算；
> 当前全为国内航班，当地时间即北京时间，直接用 DATETIME 更直观。

## 三、表结构

### 1. `airlines` 航空公司

| 字段 | 类型 | 说明 |
|---|---|---|
| id | SMALLINT UNSIGNED PK | 主键 |
| airline_code | CHAR(2) UNIQUE | IATA 二字码，如 `CA` |
| airline_name | VARCHAR(50) | 航司全称 |

### 2. `airports` 机场

| 字段 | 类型 | 说明 |
|---|---|---|
| id | SMALLINT UNSIGNED PK | 主键 |
| iata_code | CHAR(3) UNIQUE | 机场三字码，如 `PEK` |
| airport_name | VARCHAR(60) | 机场全称 |
| city | VARCHAR(30) | 所在城市（**建索引**，城市搜索走此列） |
| timezone | VARCHAR(40) | IANA 时区，默认 `Asia/Shanghai` |

### 3. `flights` 航班（核心表）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | INT UNSIGNED PK | 主键 |
| airline_id | SMALLINT UNSIGNED FK | → `airlines.id` |
| flight_no | VARCHAR(8) | 航班号，如 `CA1832` |
| dep_airport_id | SMALLINT UNSIGNED FK | → `airports.id` 起飞机场 |
| arr_airport_id | SMALLINT UNSIGNED FK | → `airports.id` 到达机场 |
| flight_date | DATE | 出发日期 |
| dep_time / arr_time | DATETIME | 计划起降时刻（当地时间） |
| duration_min | SMALLINT UNSIGNED | 飞行时长（分钟） |
| price | DECIMAL(8,2) | 票价（元），`CHECK price >= 0` |
| discount_tag | VARCHAR(10) | 特惠 / 折扣 / NULL |
| status | ENUM | 计划 / 延误 / 取消 |
| created_at | TIMESTAMP | 记录创建时间 |

**索引设计（对应查询场景）：**

| 索引 | 列 | 服务的查询 |
|---|---|---|
| `idx_search` | `(flight_date, dep_airport_id, price)` | 日期 + 出发地 + 价格上限，最常用的组合条件 |
| `idx_arr_date` | `(arr_airport_id, flight_date)` | 按到达地搜索 |
| `idx_price` | `(price)` | 按价格排序 / 筛选 |
| `idx_airline` | `(airline_id)` | 按航司统计 |
| `uk_flight_date` | `(flight_no, flight_date, dep_airport_id)` UNIQUE | 防止同一航班同一天重复落库 |

### 4. `v_flight_detail` 视图

把三张表 JOIN 成前端直接可用的一行数据，额外提供：

- `dep_city / dep_code / dep_airport`、`arr_city / arr_code / arr_airport`
- `dep_time`、`arr_time` 格式化为 `YYYY-MM-DD HH:MM`
- `dep_hm / arr_hm` 仅时分（列表页展示用）
- `duration_text` 如 `3小时25分`
- `dep_timezone / arr_timezone`

## 四、ER 关系

```
airlines 1 ────< N flights N >──── 1 airports
                    │                 ▲
                    │                 │
              dep_airport_id ─────────┘
              arr_airport_id ─────────┘
```

- 一个航司有多个航班；一个机场既是多条航班的出发地也是到达地
- `flights` 通过两个外键分别指向 `airports`，属于典型的「自反多对一」结构

## 五、建库步骤

### 方式 A：命令行

```bash
# 1. 建库建表
mysql -u root -p < db/schema.sql

# 2. 导入模拟数据
mysql -u root -p flight_db < db/seed.sql

# 3. 跑示例查询
mysql -u root -p flight_db < db/queries.sql
```

### 方式 B：Navicat / DBeaver / Workbench

1. 新建连接（本机 `127.0.0.1:3306`）
2. 打开 `db/schema.sql` → 全部执行
3. 打开 `db/seed.sql` → 全部执行
4. 刷新可见 `flight_db`，共 3 表 + 1 视图

### 数据规模

| 表 | 行数 |
|---|---|
| airlines | 8 |
| airports | 18 |
| flights | 522（2026-10-20 ~ 2026-10-26，7 天） |

票价区间 380 ~ 2000 元；含 35 条跨零点航班、少量延误/取消状态。

## 六、重新生成数据

`tools/gen_seed.py` 使用固定随机种子 `20261019`，同一份代码永远生成同一份数据。
修改以下常量即可换数据：

```python
RND = random.Random(20261019)   # 换种子 → 换一份数据
START = date(2026, 10, 20)      # 起始日期
DAYS = 7                        # 覆盖天数
```

```bash
python tools/gen_seed.py    # 覆盖写出 db/seed.sql
```

## 七、常用查询速查

```sql
-- 城市 + 日期 + 最高价（前端核心搜索）
SELECT * FROM v_flight_detail
WHERE flight_date = '2026-10-21'
  AND (dep_city = '北京' OR arr_city = '北京')
  AND price <= 1200
ORDER BY dep_time;

-- 某航线最便宜 5 班
SELECT flight_no, airline_name, dep_hm, arr_hm, price
FROM v_flight_detail
WHERE flight_date = '2026-10-22' AND dep_city = '北京' AND arr_city = '上海'
ORDER BY price LIMIT 5;
```

更多见 `db/queries.sql`（含航司均价、每日航班量、热门航线、价格分布、数据校验等 9 组）。
