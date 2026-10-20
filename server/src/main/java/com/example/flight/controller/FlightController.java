package com.example.flight.controller;

import com.example.flight.service.FlightService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 航班查询 REST 接口
 *
 * GET /api/flights?city=北京&date=2026-10-21&maxPrice=1200&airline=中国国际航空&sort=price
 * GET /api/meta
 * GET /api/health
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class FlightController {

    private final FlightService service;

    public FlightController(FlightService service) {
        this.service = service;
    }

    @GetMapping("/flights")
    public Map<String, Object> flights(
            @RequestParam(required = false) String city,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String airline,
            @RequestParam(defaultValue = "time") String sort) {
        return service.search(city, date, maxPrice, airline, sort);
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return service.meta();
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return service.health();
    }

    /** 参数错误统一返回 JSON，避免前端拿到 HTML 错误页 */
    @ExceptionHandler({IllegalArgumentException.class,
                       org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> badRequest(Exception e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 400);
        body.put("message", "参数错误：" + e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
