-- =============================================================
--  航班查询系统 —— 常用查询示例 (MySQL 8.0)
--  用法：mysql -u root -p flight_db < db/queries.sql
--  说明：注释里的 :city / :date / :max_price 是参数占位符，
--        命令行可直接替换成实际值，例如把 :city 换成 '北京'。
-- =============================================================
USE flight_db;
SET lc_time_names = 'zh_CN';   -- 让 DAYNAME() 返回中文星期

-- -------------------------------------------------------------
-- 1. 核心查询：按 城市/机场 + 出发日期 + 最高价格（可选） 搜索
--    对应前端「条件搜索」。:max_price 传 NULL 即表示不限价。
-- -------------------------------------------------------------
SELECT flight_no,
       airline_name,
       CONCAT(dep_city, ' ', dep_code) AS 起飞,
       CONCAT(arr_city, ' ', arr_code) AS 到达,
       dep_time                        AS 起飞时间,
       arr_time                        AS 到达时间,
       duration_text                   AS 飞行时长,
       price                           AS 票价,
       discount_tag
FROM v_flight_detail
WHERE flight_date = '2026-10-21'                       -- :date 出发日期
  AND (dep_city = '北京' OR arr_city = '北京')          -- :city 城市或机场
  AND (price <= 1200)                                  -- :max_price 不限价时删掉本行
ORDER BY dep_time;

-- 用机场三字码搜索（同一条件，按机场维度）
SELECT flight_no, airline_name, dep_code, arr_code, dep_time, price
FROM v_flight_detail
WHERE flight_date = '2026-10-21'
  AND (dep_code = 'PEK' OR arr_code = 'PEK')
ORDER BY price
LIMIT 10;

-- -------------------------------------------------------------
-- 2. 某天「北京 → 上海」最便宜的前 5 班
-- -------------------------------------------------------------
SELECT flight_no, airline_name, dep_hm, arr_hm, duration_text, price
FROM v_flight_detail
WHERE flight_date = '2026-10-22'
  AND dep_city = '北京'
  AND arr_city = '上海'
ORDER BY price
LIMIT 5;

-- -------------------------------------------------------------
-- 3. 各航空公司：航班量 / 最低价 / 平均价（按均价升序）
-- -------------------------------------------------------------
SELECT airline_name,
       COUNT(*)             AS 航班数,
       MIN(price)           AS 最低价,
       ROUND(AVG(price), 2) AS 平均价,
       MAX(price)           AS 最高价
FROM v_flight_detail
GROUP BY airline_name
ORDER BY 平均价;

-- -------------------------------------------------------------
-- 4. 每日航班量与平均票价（看周末溢价）
-- -------------------------------------------------------------
SELECT flight_date                    AS 日期,
       DAYNAME(flight_date)           AS 星期,
       COUNT(*)                       AS 航班数,
       ROUND(AVG(price), 0)           AS 平均票价,
       SUM(status = '延误')           AS 延误数
FROM flights
GROUP BY flight_date
ORDER BY flight_date;

-- -------------------------------------------------------------
-- 5. 最热门航线 TOP 10（按航班数，双向合并统计）
-- -------------------------------------------------------------
SELECT LEAST(dep_city, arr_city)       AS 城市A,
       GREATEST(dep_city, arr_city)    AS 城市B,
       COUNT(*)                        AS 航班数,
       ROUND(AVG(price), 0)            AS 平均票价,
       MIN(duration_min)               AS 最短飞行分钟
FROM v_flight_detail
GROUP BY 城市A, 城市B
ORDER BY 航班数 DESC, 平均票价
LIMIT 10;

-- -------------------------------------------------------------
-- 6. 机场出发航班量排行
-- -------------------------------------------------------------
SELECT dep_code            AS 机场,
       dep_airport         AS 机场名称,
       dep_city            AS 城市,
       COUNT(*)            AS 出港航班数,
       COUNT(DISTINCT arr_code) AS 通航点数量
FROM v_flight_detail
GROUP BY dep_code, dep_airport, dep_city
ORDER BY 出港航班数 DESC;

-- -------------------------------------------------------------
-- 7. 跨零点航班（到达日期晚于出发日期，验证时间字段的正确性）
-- -------------------------------------------------------------
SELECT flight_no, dep_time, arr_time, duration_text, TIMESTAMPDIFF(HOUR, dep_time, arr_time) AS 小时差
FROM v_flight_detail
WHERE DATE(arr_time) > DATE(dep_time)
ORDER BY dep_time
LIMIT 10;

-- -------------------------------------------------------------
-- 8. 价格区间分布（直方图式统计）
-- -------------------------------------------------------------
SELECT CASE
         WHEN price < 600  THEN '1) 600 以下'
         WHEN price < 900  THEN '2) 600-899'
         WHEN price < 1200 THEN '3) 900-1199'
         WHEN price < 1600 THEN '4) 1200-1599'
         ELSE                   '5) 1600 及以上'
       END                AS 价格区间,
       COUNT(*)           AS 航班数,
       ROUND(COUNT(*) * 100 / (SELECT COUNT(*) FROM flights), 1) AS 占比百分比
FROM flights
GROUP BY 价格区间
ORDER BY 价格区间;

-- -------------------------------------------------------------
-- 9. 验证数据完整性：起降时间差是否等于 duration_min
-- -------------------------------------------------------------
SELECT COUNT(*) AS 时间不一致记录数
FROM flights
WHERE TIMESTAMPDIFF(MINUTE, dep_time, arr_time) <> duration_min;
