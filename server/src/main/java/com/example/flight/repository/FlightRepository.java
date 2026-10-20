package com.example.flight.repository;

import com.example.flight.model.FlightRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 航班数据访问层：直接用 JdbcTemplate 拼动态 SQL，
 * 所有条件都用 ? 占位符绑定，避免 SQL 注入。
 */
@Repository
public class FlightRepository {

    private final JdbcTemplate jdbc;

    public FlightRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String BASE_SELECT =
            "SELECT id, airline_code, airline_name, flight_no, " +
            "       dep_city, dep_code, dep_airport, " +
            "       arr_city, arr_code, arr_airport, " +
            "       flight_date, dep_time, arr_time, dep_hm, arr_hm, " +
            "       duration_min, duration_text, price, discount_tag, status " +
            "FROM v_flight_detail ";

    private static final RowMapper<FlightRow> MAPPER = (rs, i) -> {
        FlightRow r = new FlightRow();
        r.setId(rs.getLong("id"));
        r.setAirlineCode(rs.getString("airline_code"));
        r.setAirlineName(rs.getString("airline_name"));
        r.setFlightNo(rs.getString("flight_no"));
        r.setDepCity(rs.getString("dep_city"));
        r.setDepCode(rs.getString("dep_code"));
        r.setDepAirport(rs.getString("dep_airport"));
        r.setArrCity(rs.getString("arr_city"));
        r.setArrCode(rs.getString("arr_code"));
        r.setArrAirport(rs.getString("arr_airport"));
        r.setFlightDate(rs.getString("flight_date"));
        r.setDepTime(rs.getString("dep_time"));
        r.setArrTime(rs.getString("arr_time"));
        r.setDepHm(rs.getString("dep_hm"));
        r.setArrHm(rs.getString("arr_hm"));
        r.setDurationMin(rs.getInt("duration_min"));
        r.setDurationText(rs.getString("duration_text"));
        r.setPrice(rs.getBigDecimal("price"));
        r.setDiscountTag(rs.getString("discount_tag"));
        r.setStatus(rs.getString("status"));
        return r;
    };

    /**
     * 条件搜索：城市/机场（可选）+ 出发日期（可选）+ 最高价格（可选）+ 航司（可选）
     *
     * @param city     城市名、机场三字码或机场名称片段，均可；可为 null
     * @param date     出发日期；可为 null
     * @param maxPrice 价格上限（含）；可为 null 表示不限
     * @param airline  航司全称；可为 null
     * @param sort     time / price / duration
     */
    public List<FlightRow> search(String city, LocalDate date, BigDecimal maxPrice,
                                  String airline, String sort) {
        StringBuilder sql = new StringBuilder(BASE_SELECT).append("WHERE 1 = 1 ");
        List<Object> args = new ArrayList<>();

        if (city != null && !city.trim().isEmpty()) {
            String kw = city.trim();
            sql.append("AND (dep_city = ? OR arr_city = ? ")
               .append("     OR dep_code = ? OR arr_code = ? ")
               .append("     OR dep_airport LIKE ? OR arr_airport LIKE ?) ");
            args.add(kw);
            args.add(kw);
            args.add(kw.toUpperCase());
            args.add(kw.toUpperCase());
            args.add("%" + kw + "%");
            args.add("%" + kw + "%");
        }
        if (date != null) {
            sql.append("AND flight_date = ? ");
            args.add(date);
        }
        if (maxPrice != null) {
            sql.append("AND price <= ? ");
            args.add(maxPrice);
        }
        if (airline != null && !airline.trim().isEmpty()) {
            sql.append("AND airline_name = ? ");
            args.add(airline.trim());
        }

        // 兜底上限，防止误传空条件把整库拉给前端；当前数据集 522 条，1000 足够全量返回
        sql.append(orderBy(sort)).append("LIMIT 1000");

        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    /** 排序白名单，防止 ORDER BY 注入 */
    private String orderBy(String sort) {
        if ("price".equals(sort)) {
            return "ORDER BY price ASC, dep_time ASC ";
        }
        if ("duration".equals(sort)) {
            return "ORDER BY duration_min ASC, dep_time ASC ";
        }
        return "ORDER BY flight_date ASC, dep_time ASC ";
    }

    /** 可选的出发日期列表（供前端日期下拉/提示） */
    public List<String> listDates() {
        return jdbc.queryForList(
                "SELECT DISTINCT DATE_FORMAT(flight_date, '%Y-%m-%d') d " +
                "FROM flights ORDER BY d", String.class);
    }

    /** 可选城市列表 */
    public List<String> listCities() {
        return jdbc.queryForList("SELECT DISTINCT city FROM airports ORDER BY city", String.class);
    }

    /** 可选机场列表：[三字码] 城市 机场名 */
    public List<String> listAirports() {
        return jdbc.queryForList(
                "SELECT CONCAT(iata_code, ' ', city, ' ', airport_name) FROM airports ORDER BY city, iata_code",
                String.class);
    }

    /** 可选航司列表 */
    public List<String> listAirlines() {
        return jdbc.queryForList("SELECT airline_name FROM airlines ORDER BY id", String.class);
    }

    /** 健康检查：返回数据库中的航班总数 */
    public int countFlights() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM flights", Integer.class);
        return n == null ? 0 : n;
    }
}
