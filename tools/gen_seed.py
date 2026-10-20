# -*- coding: utf-8 -*-
"""生成航班模拟数据 seed.sql（确定性随机，可重复生成同一份数据）"""
import random
from datetime import date, datetime, timedelta

RND = random.Random(20261019)

AIRLINES = [
    ("CA", "中国国际航空", 1.12),
    ("MU", "中国东方航空", 1.00),
    ("CZ", "中国南方航空", 1.00),
    ("HU", "海南航空", 1.02),
    ("ZH", "深圳航空", 0.95),
    ("MF", "厦门航空", 0.97),
    ("3U", "四川航空", 0.93),
    ("9C", "春秋航空", 0.78),
]

AIRPORTS = [
    ("PEK", "首都国际机场", "北京"),
    ("PKX", "大兴国际机场", "北京"),
    ("SHA", "虹桥国际机场", "上海"),
    ("PVG", "浦东国际机场", "上海"),
    ("CAN", "白云国际机场", "广州"),
    ("SZX", "宝安国际机场", "深圳"),
    ("CTU", "天府国际机场", "成都"),
    ("HGH", "萧山国际机场", "杭州"),
    ("XIY", "咸阳国际机场", "西安"),
    ("CKG", "江北国际机场", "重庆"),
    ("KMG", "长水国际机场", "昆明"),
    ("XMN", "高崎国际机场", "厦门"),
    ("WUH", "天河国际机场", "武汉"),
    ("NKG", "禄口国际机场", "南京"),
    ("CSX", "黄花国际机场", "长沙"),
    ("TAO", "胶东国际机场", "青岛"),
    ("SYX", "凤凰国际机场", "三亚"),
    ("HRB", "太平国际机场", "哈尔滨"),
]

ROUTES = [
    ("北京", "上海", 135), ("北京", "广州", 205), ("北京", "成都", 175), ("北京", "深圳", 215),
    ("上海", "广州", 175), ("上海", "成都", 200), ("上海", "西安", 140), ("上海", "三亚", 185),
    ("广州", "成都", 155), ("广州", "杭州", 135), ("深圳", "杭州", 140), ("深圳", "西安", 185),
    ("成都", "昆明", 95), ("杭州", "武汉", 100), ("北京", "哈尔滨", 150), ("上海", "青岛", 95),
    ("广州", "三亚", 100), ("深圳", "厦门", 85), ("成都", "南京", 165), ("西安", "重庆", 90),
    ("北京", "三亚", 240), ("上海", "哈尔滨", 165), ("昆明", "三亚", 130), ("长沙", "青岛", 130),
]

START = date(2026, 10, 20)
DAYS = 7

def airports_of(city):
    return [i + 1 for i, a in enumerate(AIRPORTS) if a[2] == city]

def q(s):
    return "'" + str(s).replace("'", "''") + "'"

rows = []
seq = {code: 1000 + RND.randint(0, 400) for code, _, _ in AIRLINES}

for d in range(DAYS):
    cur = START + timedelta(days=d)
    weekend = cur.weekday() >= 4          # 周五~周日票价上浮
    for c1, c2, base in ROUTES:
        for frm, to in ((c1, c2), (c2, c1)):
            n = 1 if RND.random() < 0.45 else 2          # 每方向每天 1~2 班
            for _ in range(n):
                code, _, fare_factor = RND.choice(AIRLINES)
                dep_id = RND.choice(airports_of(frm))
                arr_id = RND.choice(airports_of(to))
                dep_min = (6 + RND.randrange(17)) * 60 + RND.randrange(12) * 5
                duration = base + RND.randrange(-10, 11)
                # 票价：基础 + 距离 + 随机，再按航司 / 周末 / 时段浮动
                price = 280 + base * 2.4 + RND.random() * 900
                price *= fare_factor
                if weekend:
                    price *= 1.15
                if dep_min < 8 * 60:
                    price *= 0.85
                elif dep_min >= 21 * 60:
                    price *= 0.80
                price = min(3280, int(round(price / 10.0)) * 10)

                dep_dt = datetime.combine(cur, datetime.min.time()) + timedelta(minutes=dep_min)
                arr_dt = dep_dt + timedelta(minutes=duration)   # 自动处理跨零点

                seq[code] += RND.randint(3, 17)
                fno = "%s%d" % (code, seq[code])

                tag = "特惠" if price < 500 else ("折扣" if price < 900 else None)
                status = "计划"
                if RND.random() < 0.02:
                    status = "延误"
                elif RND.random() < 0.005:
                    status = "取消"

                rows.append((AIRLINES.index(next(a for a in AIRLINES if a[0] == code)) + 1,
                             fno, dep_id, arr_id, cur.isoformat(),
                             dep_dt.strftime("%Y-%m-%d %H:%M:%S"),
                             arr_dt.strftime("%Y-%m-%d %H:%M:%S"),
                             duration, price, tag, status))

out = []
out.append("-- =============================================================")
out.append("--  航班查询系统 —— 模拟数据（seed.sql）")
out.append("--  由 tools/gen_seed.py 生成，随机种子 20261019，可重复复现")
out.append("--  日期范围：2026-10-20 ~ 2026-10-26，共 %d 条航班" % len(rows))
out.append("--  时区：Asia/Shanghai (UTC+8)，DATETIME 为当地时间")
out.append("-- =============================================================")
out.append("SET NAMES utf8mb4;")
out.append("USE flight_db;")
out.append("")
out.append("-- 清理旧数据（按外键顺序，可重复执行）")
out.append("SET FOREIGN_KEY_CHECKS = 0;")
out.append("TRUNCATE TABLE flights;")
out.append("TRUNCATE TABLE airlines;")
out.append("TRUNCATE TABLE airports;")
out.append("SET FOREIGN_KEY_CHECKS = 1;")
out.append("")

out.append("-- ---------------- 航空公司 ----------------")
vals = ",\n".join("  (%d, %s, %s)" % (i + 1, q(c), q(n)) for i, (c, n, _) in enumerate(AIRLINES))
out.append("INSERT INTO airlines (id, airline_code, airline_name) VALUES\n%s;" % vals)
out.append("")

out.append("-- ---------------- 机场 ----------------")
vals = ",\n".join("  (%d, %s, %s, %s, 'Asia/Shanghai')" % (i + 1, q(c), q(n), q(city))
                  for i, (c, n, city) in enumerate(AIRPORTS))
out.append("INSERT INTO airports (id, iata_code, airport_name, city, timezone) VALUES\n%s;" % vals)
out.append("")

out.append("-- ---------------- 航班 ----------------")
CHUNK = 100
for i in range(0, len(rows), CHUNK):
    part = rows[i:i + CHUNK]
    out.append("INSERT INTO flights (airline_id, flight_no, dep_airport_id, arr_airport_id, "
               "flight_date, dep_time, arr_time, duration_min, price, discount_tag, status) VALUES")
    body = []
    for (aid, fno, dep, arr, d, dt1, dt2, dur, pr, tag, st) in part:
        body.append("  (%d, %s, %d, %d, %s, %s, %s, %d, %.2f, %s, %s)" % (
            aid, q(fno), dep, arr, q(d), q(dt1), q(dt2), dur, pr,
            "NULL" if tag is None else q(tag), q(st)))
    out.append(",\n".join(body) + ";")
    out.append("")

out.append("-- 数据校验")
out.append("SELECT 'airlines' AS t, COUNT(*) AS cnt FROM airlines")
out.append("UNION ALL SELECT 'airports', COUNT(*) FROM airports")
out.append("UNION ALL SELECT 'flights',  COUNT(*) FROM flights;")

with open("db/seed.sql", "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(out) + "\n")

print("rows:", len(rows))
print("date range:", START, "~", START + timedelta(days=DAYS - 1))
print("price range:", min(r[8] for r in rows), "-", max(r[8] for r in rows))
print("overnight flights:", sum(1 for r in rows if r[5][:10] != r[6][:10]))
