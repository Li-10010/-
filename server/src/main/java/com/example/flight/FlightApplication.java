package com.example.flight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 航班查询系统后端入口
 * 启动后：前端页面 http://localhost:8080/  API http://localhost:8080/api/flights
 */
@SpringBootApplication
public class FlightApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlightApplication.class, args);
    }
}
