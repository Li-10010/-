# 航班查询系统 · 后端（Java / Spring Boot）

## 技术栈

| 项 | 版本 / 说明 |
|---|---|
| 语言 | Java 8（本机 JDK `D:\coder\jdk1.8.0_201`） |
| 框架 | Spring Boot 2.7.18（Java 8 可用的最后一个大版本） |
| 数据访问 | `JdbcTemplate` + 动态 SQL（全部 `?` 占位符，防注入） |
| 数据库 | MySQL 8.0，库名 `flight_db` |
| 驱动 | `mysql-connector-java 8.0.33` |
| 端口 | 8080（同时托管前端静态页） |

> 若以后升级到 JDK 17+，把 `pom.xml` 里的 parent 换成 `spring-boot-starter-parent:3.2.x`、`java.version` 改成 17 即可，业务代码无需改动。

## 一、启动前准备

### 1. 建库（只做一次）

```bash
cd "C:/Users/Y9000P/Desktop/实验室3"
"/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -uroot -p < db/schema.sql
"/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -uroot -p flight_db < db/seed.sql
```

### 2. 告诉后端数据库密码

`application.yml` 里已把本机开发密码设为默认值，直接启动即可：

```yaml
username: ${MYSQL_USER:root}
password: ${MYSQL_PASSWORD:123456}   # 本机 root 密码；换环境用环境变量覆盖
```

换机器 / 换密码时，用环境变量覆盖（推荐，不把密码写进代码）：

```bash
export MYSQL_USER=root
export MYSQL_PASSWORD=你的密码
```

也可以启动时传参：

```bash
java -jar target/flight-server-1.0.0.jar --spring.datasource.password=你的密码
```

## 二、运行

**最省事**：双击 `server/start-server.bat`（已固定 JDK 路径与端口）。

命令行方式：

```bash
cd "C:/Users/Y9000P/Desktop/实验室3/server"
export JAVA_HOME="D:/coder/jdk1.8.0_201"
export PATH="/c/Users/Y9000P/.workbuddy/binaries/maven/apache-maven-3.9.16/bin:$PATH"

mvn spring-boot:run          # 开发模式
# 或
mvn package -DskipTests && java -jar target/flight-server-1.0.0.jar
```

启动后打开 **http://localhost:8080/** —— 前端页面由 Spring Boot 直接托管，
页面上方会显示绿色「已连接 MySQL 数据库」。前端不在 `server/` 内，而是通过
`application.yml` 的 `static-locations` 指向项目根目录的 `web/`，前后端只维护一份 HTML。

## 三、接口

### `GET /api/flights` 条件查询

| 参数 | 必填 | 示例 | 说明 |
|---|---|---|---|
| `city` | 否 | `北京` / `PEK` / `虹桥` | 城市名、机场三字码、机场名片段，匹配出发或到达 |
| `date` | 否 | `2026-10-21` | 出发日期，格式 `yyyy-MM-dd` |
| `maxPrice` | 否 | `1200` | 价格上限（含），不传=不限 |
| `airline` | 否 | `中国国际航空` | 航司全称，不传=全部 |
| `sort` | 否 | `time` / `price` / `duration` | 默认 `time`（起飞时间） |

```bash
curl "http://localhost:8080/api/flights?city=北京&date=2026-10-21&maxPrice=1200&sort=price"
```

响应：

```json
{
  "code": 0,
  "total": 9,
  "query": { "city": "北京", "date": "2026-10-21", "maxPrice": 1200, "airline": null, "sort": "price" },
  "data": [
    {
      "id": 128,
      "airlineCode": "9C",
      "airlineName": "春秋航空",
      "flightNo": "9C2141",
      "depCity": "北京", "depCode": "PKX", "depAirport": "大兴国际机场",
      "arrCity": "上海", "arrCode": "PVG", "arrAirport": "浦东国际机场",
      "flightDate": "2026-10-21",
      "depTime": "2026-10-21 07:25", "arrTime": "2026-10-21 09:40",
      "depHm": "07:25", "arrHm": "09:40",
      "durationMin": 135, "durationText": "2小时15分",
      "price": 640.00, "discountTag": "折扣", "status": "计划"
    }
  ]
}
```

### `GET /api/meta` 下拉选项

返回 `cities` / `airports` / `airlines` / `dates` / `timezone` / `timeFormat`，
前端启动时用它填充输入建议和日期范围。

### `GET /api/health` 健康检查

```json
{ "status": "UP", "flights": 522 }
```

数据库连不上时返回 `{"status":"DOWN","error":"..."}`，可用于排查配置。

## 四、代码结构

```
server/src/main/java/com/example/flight/
├── FlightApplication.java          启动类
├── controller/FlightController.java  REST 接口 + 参数异常处理
├── service/FlightService.java        参数校验、组装响应
├── repository/FlightRepository.java  动态 SQL（JdbcTemplate）
└── model/FlightRow.java              查询结果对象
```

分层职责：Controller 只负责接参数、Service 负责校验与响应结构、Repository 负责 SQL。

## 五、设计要点

1. **SQL 注入防护**：所有用户输入都通过 `?` 占位符传给 `JdbcTemplate`；
   `sort` 走白名单映射，不拼接原始字符串。
2. **查询总量保护**：单次最多返回 500 条（`LIMIT 500`），避免前端一次渲染过多节点。
3. **时区**：JDBC URL 显式带 `serverTimezone=Asia/Shanghai`，否则 MySQL 驱动
   可能按 UTC 解释 `DATETIME`，导致页面时刻偏移 8 小时。
4. **前后端同源**：静态资源与接口同一个端口，省掉跨域配置（仍保留 `@CrossOrigin` 便于分离部署）。
5. **前端可降级**：没有后端时自动使用内置演示数据，方便单独演示页面。

## 六、常见问题

| 现象 | 原因 | 解决 |
|---|---|---|
| 启动报 `Access denied for user 'root'` | 密码未配置 | 设 `MYSQL_PASSWORD` 环境变量或改 yml |
| 启动报 `Unknown database 'flight_db'` | 没建库 | 先执行 `db/schema.sql`、`db/seed.sql` |
| 页面显示「未检测到后端服务」 | 后端没启动 / 端口不是 8080 | 启动服务，或用正确的 `server.port` |
| 时间比数据库里的多 8 小时 | 时区参数缺失 | 检查 JDBC URL 的 `serverTimezone` |
| 中文乱码 | 库/连接字符集不对 | 库用 `utf8mb4`，URL 带 `characterEncoding=UTF-8`（不能写 utf8mb4） |
| 双击 start-server.bat 报错/闪退 | bat 内含中文，GBK 控制台下乱码导致 java 没启动 | 已改为纯 ASCII；自建 bat 不要写中文 |
| 浏览器打不开 localhost:8080 | 服务没启动（进程已退出） | 双击 `start-server.bat`，黑窗口保持开着不要关 |

## 七、联调实测结果（2026-10-19 已跑通）

| 检查项 | 结果 |
|---|---|
| 建库 | `flight_db` 创建成功，3 表 + 1 视图 |
| 数据导入 | airlines 8 / airports 18 / flights **522** |
| 服务启动 | 2.5 秒，Tomcat 8080 |
| `GET /api/health` | `{"status":"UP","flights":522}` |
| `GET /` | HTTP 200，返回 17KB 页面 |
| 条件查询 | 北京 + 2026-10-21 + ≤1200 元 → 15 条，按价格升序正确 |
| 机场码查询 | `city=PEK` + 2026-10-22 → 11 条 |
| 航司 + 时长排序 | 春秋航空 → 67 条，最短 1小时21分 |
| 参数校验 | `date=abc` → 400；`maxPrice=-5` → 400（均返回 JSON） |
| 全量查询 | `total=522`（单次上限 1000 条） |
| 数据一致性 | 起降时间差 ≠ 时长 的记录数 = 0 |

实测输出片段（北京出发/到达，2026-10-21，≤1200 元，按价格升序）：

```
9C1311 春秋航空 北京PKX -> 成都CTU 20:40 2小时46分 ¥560
3U1371 四川航空 北京PKX -> 成都CTU 07:50 2小时56分 ¥640
ZH1202 深圳航空 北京PEK -> 上海PVG 08:40 2小时24分 ¥660
HU1254 海南航空 哈尔滨HRB -> 北京PEK 09:00 2小时26分 ¥680
MF1375 厦门航空 北京PEK -> 广州CAN 21:55 3小时17分 ¥710
```

注意 `city` 参数是「出发或到达」双向匹配，所以哈尔滨→北京也会命中，符合"按城市搜索"的语义。
