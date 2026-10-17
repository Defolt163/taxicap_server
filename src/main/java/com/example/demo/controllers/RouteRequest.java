package com.example.demo.controllers;

public class RouteRequest {
    public String from;
    public String to;
    public Coords coordsFrom;
    public Coords coordsTo;

    public static class Coords {
        public Double lat;
        public Double lon;
    }
}