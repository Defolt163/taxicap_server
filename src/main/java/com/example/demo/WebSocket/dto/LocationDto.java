package com.example.demo.WebSocket.dto;

import lombok.Data;

@Data
public class LocationDto {
    private String orderId;
    private Double lat;
    private Double lon;
}