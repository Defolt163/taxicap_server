package com.example.demo.WebSocket.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "customerPhone")
    private Long customerPhone;

    @Column(name = "userId")
    private Integer userId;

    @Column(name = "driverName")
    private String driverName;

    @Column(name = "driverId")
    private Integer driverId;

    @Column(name = "driverPhone")
    private Long driverPhone;

    @Column(name = "vehicleBrand")
    private String vehicleBrand;

    @Column(name = "vehicleModel")
    private String vehicleModel;

    @Column(name = "vehicleColor")
    private String vehicleColor;

    @Column(name = "vehicleNumber")
    private String vehicleNumber;

    @Column(name = "orderStatus")
    private String orderStatus;

    @Column(name = "customerName")
    private String customerName;

    @Column(name = "latFrom")
    private String latFrom;

    @Column(name = "lonFrom")
    private String lonFrom;

    @Column(name = "latTo")
    private String latTo;

    @Column(name = "lonTo")
    private String lonTo;

    @Column(name = "addressFrom")
    private String addressFrom;

    @Column(name = "addressTo")
    private String addressTo;

    @Column(name = "price")
    private Integer price;

    @Column(name = "paymentMethod")
    private String paymentMethod;

    @Column(name = "date")
    private Timestamp date;

    @Column(name = "driverImage")
    private String driverImage;

    @Column(name = "customerImage")
    private String customerImage;

    @Column(name = "encodedWay")
    private String encodedWay;

    @Column(name = "routeDistanceMeters")
    private String routeDistanceMeters;

    @Column(name = "routeDistanceKm")
    private String routeDistanceKm;
}