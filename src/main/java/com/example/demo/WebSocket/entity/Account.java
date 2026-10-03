package com.example.demo.WebSocket.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.sql.Timestamp;

@Entity
@Table(name = "accounts")
@Data
public class Account {

    @Id
    @Column(name = "UserId")
    private Integer userId;

    @Column(name = "UserName")
    private String userName;

    @Column(name = "UserPhone")
    private Long userPhone;

    @Column(name = "UserImage")
    private String userImage;

    @Column(name = "VehicleBrand")
    private String vehicleBrand;

    @Column(name = "VehicleModel")
    private String vehicleModel;

    @Column(name = "VehicleColor")
    private String vehicleColor;

    @Column(name = "VehicleNumber")
    private String vehicleNumber;

    @Column(name = "ActiveOrder")
    private Integer activeOrder;
}