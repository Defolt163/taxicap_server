package com.example.demo.WebSocket.exceptions;

public class OrderUnavailableException extends RuntimeException {
    private final Integer orderId;

    public OrderUnavailableException(Integer orderId) {
        super("Заказ уже принят или недоступен");
        this.orderId = orderId;
    }

    public Integer getOrderId() { return orderId; }
}