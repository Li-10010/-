package com.example.flight.model;

import java.math.BigDecimal;

/**
 * 航班查询结果行，字段与视图 v_flight_detail 一一对应。
 * 时间字段统一为字符串：yyyy-MM-dd HH:mm（机场当地时间，Asia/Shanghai，UTC+8）。
 */
public class FlightRow {

    private Long id;
    private String airlineCode;   // 航司二字码 CA
    private String airlineName;   // 航司全称
    private String flightNo;      // 航班号

    private String depCity;       // 出发城市
    private String depCode;       // 出发机场三字码
    private String depAirport;    // 出发机场全称

    private String arrCity;       // 到达城市
    private String arrCode;       // 到达机场三字码
    private String arrAirport;    // 到达机场全称

    private String flightDate;    // 出发日期 yyyy-MM-dd
    private String depTime;       // 计划起飞 yyyy-MM-dd HH:mm
    private String arrTime;       // 计划到达 yyyy-MM-dd HH:mm
    private String depHm;         // 起飞时分 HH:mm
    private String arrHm;         // 到达时分 HH:mm

    private Integer durationMin;  // 飞行时长（分钟）
    private String durationText;  // 例如 3小时25分

    private BigDecimal price;     // 票价（元）
    private String discountTag;   // 特惠 / 折扣 / null
    private String status;        // 计划 / 延误 / 取消

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAirlineCode() { return airlineCode; }
    public void setAirlineCode(String airlineCode) { this.airlineCode = airlineCode; }

    public String getAirlineName() { return airlineName; }
    public void setAirlineName(String airlineName) { this.airlineName = airlineName; }

    public String getFlightNo() { return flightNo; }
    public void setFlightNo(String flightNo) { this.flightNo = flightNo; }

    public String getDepCity() { return depCity; }
    public void setDepCity(String depCity) { this.depCity = depCity; }

    public String getDepCode() { return depCode; }
    public void setDepCode(String depCode) { this.depCode = depCode; }

    public String getDepAirport() { return depAirport; }
    public void setDepAirport(String depAirport) { this.depAirport = depAirport; }

    public String getArrCity() { return arrCity; }
    public void setArrCity(String arrCity) { this.arrCity = arrCity; }

    public String getArrCode() { return arrCode; }
    public void setArrCode(String arrCode) { this.arrCode = arrCode; }

    public String getArrAirport() { return arrAirport; }
    public void setArrAirport(String arrAirport) { this.arrAirport = arrAirport; }

    public String getFlightDate() { return flightDate; }
    public void setFlightDate(String flightDate) { this.flightDate = flightDate; }

    public String getDepTime() { return depTime; }
    public void setDepTime(String depTime) { this.depTime = depTime; }

    public String getArrTime() { return arrTime; }
    public void setArrTime(String arrTime) { this.arrTime = arrTime; }

    public String getDepHm() { return depHm; }
    public void setDepHm(String depHm) { this.depHm = depHm; }

    public String getArrHm() { return arrHm; }
    public void setArrHm(String arrHm) { this.arrHm = arrHm; }

    public Integer getDurationMin() { return durationMin; }
    public void setDurationMin(Integer durationMin) { this.durationMin = durationMin; }

    public String getDurationText() { return durationText; }
    public void setDurationText(String durationText) { this.durationText = durationText; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getDiscountTag() { return discountTag; }
    public void setDiscountTag(String discountTag) { this.discountTag = discountTag; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
