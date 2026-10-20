# 更新日志 (CHANGELOG)

本项目按时间倒序记录，版本号规则：`主版本.功能版本.修订版本`。
所有日期均为 **2026 年 10 月 19 日**（当日连续开发完成）。

---

## [v1.2.4] — 2026-10-20 14:55

### 变更
- **主页面（登录后界面）去掉数据库相关提示**，共 5 处文案：
  - 页头副标题 `MySQL 数据库 · Java 后端接口 · 条件搜索` → `国内航班查询 · 多条件筛选`
  - 状态栏初始文案 `正在检测后端服务…` → `正在加载航班数据…`
  - 搜索成功 `已连接 MySQL 数据库（Java 后端 · flight_db）` → `航班数据已更新`
  - 首次加载成功 `已连接 MySQL 数据库 · 共 N 条航班（Java 后端 · flight_db）` → `已更新航班数据 · 共 N 条`
  - 降级提示 `未检测到后端服务，当前使用内置演示数据（启动 Java 服务后自动切换）` → `当前展示演示数据（连接恢复后自动更新）`
  - 页脚 `数据来源：MySQL flight_db.v_flight_detail · 所有时刻为机场当地时间（UTC+8）` → `所有时刻均为机场当地时间（UTC+8）`
- 仅改文案，`setStatus()` 的布尔参数（绿/橙状态灯）与请求逻辑均未动，代码注释保留说明性内容。

### 实测
源码与线上返回中 `MySQL` / `flight_db` / `数据库` / `v_flight_detail` grep 计数全为 0，脚本语法检查通过，接口 `/api/flights` 仍正常返回。

---

## [v1.2.3] — 2026-10-20 14:52

### 变更
- **登录页两个标签位置互换**：由「登 录 ｜ 注 册」改为「注 册 ｜ 登 录」。仅调换 DOM 顺序，
  `data-mode` 与事件绑定不受影响，默认仍选中「登录」（打开页面即登录态）。

### 实测
页面源码与线上返回的标签顺序均为「注 册 / 登 录」，`active` 在「登 录」上；脚本语法检查通过。

---

## [v1.2.2] — 2026-10-20 14:50

### 变更
- **登录页去掉数据库相关提示**：删除卡片底部那段 `.hint`
  （「账号写入 MySQL `flight_db.users`，每次登录记入 `login_logs`…密码以 PBKDF2…」），
  同时清掉随之失效的 `.hint` / `.hint code` 样式。
- 品牌副标题 `登录后查询 flight_db 中的国内航班数据` → `登录后查询国内航班数据`，不再暴露库名。
- **去掉页脚「演示账号：demo / test1234」**，连带清掉随之失效的 `.foot` 样式；`demo` 账号在库中保留，仍可正常登录。

### 实测
页面源码与线上返回均已无 `MySQL` / `flight_db` / `login_logs` / `PBKDF2` / `hint` / `demo` / `test1234` 字样（grep 计数全 0），脚本语法检查通过，`demo` 账号登录仍返回 200。

---

## [v1.2.1] — 2026-10-20 14:46

### 变更
- **注册页去掉昵称输入框**：`web/login.html` 的注册表单现在只保留「用户名 / 密码 / 确认密码」三项，
  请求体不再携带 `displayName`；同步清理了 `switchMode` 里的显隐逻辑与表单取值代码。
- 后端接口保持向后兼容：`displayName` 仍是可选参数，不传则 `users.display_name` 写 `NULL`，
  接口返回时用 `username` 兜底，页面右上角显示的就是用户名。

### 实测
| 场景 | 结果 |
|---|---|
| `POST /api/auth/register` 只传 username + password | 200，`users.display_name` 为 `NULL`，返回 `displayName: "bob"`（用户名兜底） |
| 页面源码检查 | 已无 `id="displayName"` 输入框 |
| 前端脚本语法检查 | 通过 |

---

## [v1.2.0] — 2026-10-20 14:40

### 新增（登录模块）
- **数据库**：新增 `users`（账号表）与 `login_logs`（登录日志）两张表。
  - 独立脚本 `db/auth.sql`，`CREATE TABLE IF NOT EXISTS` + `WHERE NOT EXISTS` 插入演示账号，**可对已有库重复执行**；
  - 同步写入 `db/schema.sql`（第 5、6 节），全新建库时会一并创建。
- **后端**（分层与航班模块一致）：
  - `AuthController` — `POST /api/auth/register`、`POST /api/auth/login`、`GET /api/auth/me`、`POST /api/auth/logout`
  - `AuthService` — 注册/登录/会话校验，token 用 UUID，内存保存 2 小时
  - `UserRepository` — JdbcTemplate，全部 `?` 占位符绑定
  - `PasswordHasher` — PBKDF2-HMAC-SHA256，10 万次迭代、16 字节随机盐，常量时间比较
- **前端**：新增 `web/login.html`（登录/注册双标签，密码可见切换、错误提示、已登录续访）；
  `web/index.html` 增加登录守卫（http 下无 token 跳登录页）与右上角用户区（头像/昵称/退出登录）。
- **安全细节**：任何响应都不含 `password_hash`；登录成功/失败均落 `login_logs`（含 IP、原因）；
  同一账号 5 分钟内失败 5 次返回 429；用户名唯一索引为 `utf8mb4_0900_ai_ci`，`ALICE` 与 `alice` 视为同一账号。

### 实测记录（2026-10-20 14:36，curl）
| 场景 | 结果 |
|---|---|
| `demo` / `test1234` 登录 | 200，返回 token + 用户信息 |
| 错误密码 | 401 `用户名或密码错误` |
| 注册 `alice` / 小艾 | 200，自动登录，`users` 新增 1 行 |
| 重复注册 `ALICE` | 400 `用户名已被注册`（大小写不敏感生效） |
| 用户名含空格 / 密码 3 位 | 400，参数校验生效 |
| `/api/auth/me` 无 token、退出后再用 | 均 401 |
| 库内密码字段 | 90 字符 `pbkdf2_sha256$100000$…`，无明文 |

### 说明
- 为不破坏已有 curl 联调与「离线演示数据」能力，`/api/flights`、`/api/meta` 仍**未强制鉴权**，
  登录态目前由前端守卫 + token 校验构成；需要后端强制拦截可在 `WebConfig` 里加 `HandlerInterceptor`。
- 会话保存在内存，重启后端需重新登录；要多实例或持久会话可改 JWT 或把 token 落库。


---

## [v1.1.2] — 2026-10-19 23:20

### 修复
- **修复双击 `start-server.bat` 报错/闪退**：批处理文件内含中文注释，在 Windows 默认 GBK(936) 控制台下被解析成乱码，导致 cmd 报「不是内部或外部命令」且 java 进程从未启动。已将 bat 内容改为**纯 ASCII**，实测启动成功（8080 监听、首页 200、health UP）。
- **修正 `server/README.md` 排错表笔误**：`characterEncoding=utf8mb4` → `UTF-8`（驱动只认 Java 字符集名）。
- **排错表补充两条**：bat 不得含中文；页面打不开先确认服务是否在运行。

### 文档
- `README.md` / `server/README.md` 同步"数据库已建好"的实际状态，启动步骤简化为双击 bat。
- `server/README.md` 新增「七、联调实测结果」表，记录真实查询输出。

---

## [v1.1.1] — 2026-10-19 23:05

### 修复
- **修复数据库连接失败 `Unsupported character encoding 'utf8mb4'`**：
  JDBC URL 的 `characterEncoding` 必须是 Java 字符集名（`UTF-8`），不能写 MySQL 的 `utf8mb4`。已在 `application.yml` 中改正并加注释。
- **修复全量查询被截断**：单次返回上限 `LIMIT 500` 会把 522 条数据截成 500 条，调整为 `LIMIT 1000`。
- **接口异常统一返回 JSON**：新增 `GlobalExceptionHandler`，替换 Spring 默认 Whitelabel HTML 错误页；并对常见数据库故障（认证失败/库不存在/连不上）给出中文可操作提示。

### 变更
- `application.yml` 数据库密码默认值改为 `${MYSQL_PASSWORD:123456}`（本机开发可直接启动），换环境仍可用环境变量覆盖。
- 新增 `server/start-server.bat` 一键启动脚本。

---

## [v1.1.0] — 2026-10-19 22:50

### 新增（Java 后端）
- 新增 `server/` 模块：**Spring Boot 2.7.18 + JdbcTemplate**（因本机仅 JDK 1.8，无法使用 Spring Boot 3；代码升级到 JDK 17 只需改 `pom.xml` 两行）。
- 依赖：`spring-boot-starter-web` / `-jdbc` / `-validation`、`mysql-connector-java 8.0.33`。
- REST 接口：
  - `GET /api/flights` — 条件查询，参数 `city` / `date` / `maxPrice` / `airline` / `sort`（时间、价格、时长三种排序）
  - `GET /api/meta` — 城市/机场/航司/可选日期，供前端下拉与日期范围
  - `GET /api/health` — 连通性自检，返回航班总数
- 分层：`FlightController` → `FlightService` → `FlightRepository`（动态 SQL，**全部 `?` 占位符**，`sort` 走白名单，防注入）。
- 端口 8080 **同时托管前端静态页**（`static-locations: file:./web/`），前后端只维护一份 HTML。

### 新增（前端改造）
- `web/index.html` 改为调用 `/api/flights` 拿数据，新增数据源状态条：
  - 连上库 → 绿色「已连接 MySQL 数据库」
  - 后端未启动 → 黄色「未检测到后端服务」，**自动降级**为内置演示数据，页面不白屏
- 下拉项改由 `/api/meta` 填充；日期输入范围由后端可选日期限定。
- 卡片新增航班状态标签（延误/取消）。

### 环境
- 本机无 Maven，新增便携版 **Maven 3.9.16**（`C:\Users\Y9000P\.workbuddy\binaries\maven`），`conf/settings.xml` 已配阿里云镜像加速。

---

## [v1.0.0] — 2026-10-19 21:52

### 新增（初始版本）
- **前端单页应用** `web/index.html`：航班卡片列表 + 条件搜索（城市/机场、出发日期、最高价格、航司）+ 三种排序，零依赖、可直接双击打开。
- **数据库层** `db/`：
  - `schema.sql` — 建库 `flight_db`（utf8mb4）+ 3 表 + 1 视图
    - `airlines`（航司）、`airports`（机场，含 `timezone` 字段）、`flights`（航班，外键 ×3）
    - 约束：`CHECK (arr_time > dep_time)`、`CHECK (price >= 0)`、`UNIQUE(flight_no, flight_date, dep_airport_id)`
    - 索引：组合索引 `idx_search(flight_date, dep_airport_id, price)`、`idx_arr_date`、`idx_price`、`idx_airline`
    - 视图 `v_flight_detail` — 三表 JOIN，输出 `dep_city/dep_code/duration_text/dep_hm` 等展示字段
  - `seed.sql` — **522 条**模拟航班（2026-10-20 ~ 10-26，8 航司、18 机场、24 条航线双向）
  - `queries.sql` — 9 组示例查询（条件搜索、最便宜 Top5、航司均价、每日航班量、热门航线、机场排行、跨零点航班、价格分布、数据校验）
- `tools/gen_seed.py` — 数据生成脚本，固定随机种子 `20261019`，可复现、可改参数重新生成。
- 时区与格式约定：统一 **Asia/Shanghai (UTC+8)**，`DATETIME` 存机场当地时间，格式 `YYYY-MM-DD HH:MM:SS`；跨零点航班 `arr_time` 自动顺延次日（全库 35 条）。
- `README.md` 数据库设计文档（表结构、ER 关系、索引设计、建库步骤）。

### 已知问题（当日已修复）
- ~~工作区存在 0 字节的空文件 `web` 挡住目录创建~~ → 已替换为目录（无数据损失）。
- ~~数据仅存在 JS 内存中，无持久化~~ → 由 v1.0.0 的数据库层 + v1.1.0 的后端解决。

---

## 待办 / 后续计划

| 项 | 说明 |
|---|---|
| `direction` 参数 | `city` 目前双向匹配（出发或到达都命中），计划加 `direction=dep/arr` 只查单方向 |
| 国际航线时区 | 现按"全部国内航班、DATETIME 存当地时间"设计；接国际航线需改为 UTC 存储 + 展示层换算 |
| 分页 | 单次上限 1000 条，数据量继续增长后应改为 page/size 分页 |
| 后端测试 | 补 JUnit 测试（当前用 curl 手工验证） |
| 打包脚本 | 输出 `target/*.jar` 已可用，可再补一条 `mvn package` 的说明到根 README |

---

## 环境与依赖备忘

| 组件 | 版本 / 位置 |
|---|---|
| JDK | 1.8.0_201 — `D:\coder\jdk1.8.0_201` |
| Maven | 3.9.16 — `C:\Users\Y9000P\.workbuddy\binaries\maven\apache-maven-3.9.16` |
| MySQL | 8.0 — `C:\Program Files\MySQL\MySQL Server 8.0`，3306，root / 123456 |
| Spring Boot | 2.7.18（Java 8 兼容；升级 JDK 17+ 后可换 3.2.x） |
| 数据规模 | airlines 8 / airports 18 / flights 522 |
