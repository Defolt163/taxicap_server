package com.example.demo.WebSocket.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class OrderDto {
    private String orderId;

    private String customerPhone;
    private String userId;
    private String orderStatus;
    private String customerName;
    private Double latFrom;
    private Double lonFrom;
    private Double latTo;
    private Double lonTo;
    private String addressFrom;
    private String addressTo;
    private BigDecimal price;
    private String paymentMethod;
    private String customerImage;
    private String encodedWay;
    private String routeDistanceMeters;
    private String routeDistanceKm;
}