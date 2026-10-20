package com.example.flight.service;

import com.example.flight.model.FlightRow;
import com.example.flight.repository.FlightRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FlightService {

    private final FlightRepository repository;

    public FlightService(FlightRepository repository) {
        this.repository = repository;
    }

    public Map<String, Object> search(String city, LocalDate date, BigDecimal maxPrice,
                                      String airline, String sort) {
        if (maxPrice != null && maxPrice.signum() < 0) {
            throw new IllegalArgumentException("最高价格不能为负数");
        }
        List<FlightRow> rows = repository.search(city, date, maxPrice, airline, sort);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", 0);
        resp.put("total", rows.size());
        resp.put("query", query(city, date, maxPrice, airline, sort));
        resp.put("data", rows);
        return resp;
    }

    public Map<String, Object> meta() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cities", repository.listCities());
        m.put("airports", repository.listAirports());
        m.put("airlines", repository.listAirlines());
        m.put("dates", repository.listDates());
        m.put("timezone", "Asia/Shanghai (UTC+8)");
        m.put("timeFormat", "yyyy-MM-dd HH:mm");
        return m;
    }

    public Map<String, Object> health() {
        Map<String, Object> h = new LinkedHashMap<>();
        try {
            h.put("status", "UP");
            h.put("flights", repository.countFlights());
        } catch (Exception e) {
            h.put("status", "DOWN");
            h.put("error", e.getMessage());
        }
        return h;
    }

    private Map<String, Object> query(String city, LocalDate date, BigDecimal maxPrice,
                                      String airline, String sort) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("city", city);
        q.put("date", date == null ? null : date.toString());
        q.put("maxPrice", maxPrice);
        q.put("airline", airline);
        q.put("sort", sort == null ? "time" : sort);
        return q;
    }
}
