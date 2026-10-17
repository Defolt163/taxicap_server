package com.example.demo.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
public class SearchAddresses {
    @Autowired
    private JwtValidator jwtValidator;

    @Value("${spring.geoborder}")
    private String geoborder;

    @Value("${services.nominatim.url:http://localhost:8003}")
    private String nominatimServerUrl;

    @Value("${services.valhalla.url:http://localhost:8002}")
    private String valhallaServerUrl;
    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/api/geo/**")
    public ResponseEntity<?> processRequestBody(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody RouteRequest requestBody) {

        // 1. Проверяем наличие заголовка
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        // 2. Проверяем JWT
        try {
            jwtValidator.validateToken(token);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(("Invalid JWT: " + e.getMessage()));
        }

        // 3. Получаем адрес из body

        String fromAddress = requestBody.from;
        String toAddress = requestBody.to;

        RouteRequest.Coords fromCoordinates = requestBody.coordsFrom;
        RouteRequest.Coords toCoordinates = requestBody.coordsTo;

        System.out.println("From: " + fromAddress);
        System.out.println("To: " + toAddress);

        if (fromCoordinates != null) {
            System.out.println("Coord From: " + fromCoordinates.lat + ", " + fromCoordinates.lon);
        } else {
            System.out.println("Coord From: null");
        }

        if (toCoordinates != null) {
            System.out.println("Coord To: " + toCoordinates.lat + ", " + toCoordinates.lon);
        } else {
            System.out.println("Coord To: null");
        }

        if (fromAddress == null || toAddress == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Ошибка ввода");
        }

        String nominationUrlFrom = nominatimServerUrl + "/search?viewbox=" + geoborder + "&format=jsonv2&q="
                + fromAddress + "&bounded=1";
        String nominationUrlTo = nominatimServerUrl + "/search?viewbox=" + geoborder + "&format=jsonv2&q="
                + toAddress + "&bounded=1";
        //System.out.print(nominationUrlFrom);
        try {
            ObjectMapper mapper = new ObjectMapper();
            String fromLat;
            String fromLon;
            if(fromCoordinates != null){
                fromLat = fromCoordinates.lat.toString();
                fromLon = fromCoordinates.lon.toString();
                System.out.println("ОТ Запрос без номинатима");
            }else{
                String responseFrom = restTemplate.getForObject(nominationUrlFrom, String.class);
                JsonNode[] resultsFrom = mapper.readValue(responseFrom, JsonNode[].class);

                if (resultsFrom.length == 0) {
                    return ResponseEntity.badRequest().body("Адрес отправления не найден");
                }

                fromLat = resultsFrom[0].get("lat").toString();
                fromLon = resultsFrom[0].get("lon").toString();
                System.out.println("ОТ Запрос с номинатимом");
            }

            // Задержка в 1 секунду (согласно политике Nominatim)
            Thread.sleep(1000);

            // Получаем координаты TO
            String toLat;
            String toLon;
            if(toCoordinates != null){
                toLat = toCoordinates.lat.toString();
                toLon = toCoordinates.lon.toString();
                System.out.println(toLon);
            }else{
                String responseTo = restTemplate.getForObject(nominationUrlTo, String.class);
                JsonNode[] resultsTo = mapper.readValue(responseTo, JsonNode[].class);

                if (resultsTo.length == 0) {
                    return ResponseEntity.badRequest().body("Адрес назначения не найден, или находится вне зоны действия приложения");
                }

                toLat = resultsTo[0].get("lat").toString();
                toLon = resultsTo[0].get("lon").toString();
                System.out.println("Куда Запрос с номинатимом");
            }
            

            //
            // System.out.print(toLat);

            String dataRoute = String.format(
                    "{\"locations\":[{\"lat\":%s,\"lon\":%s},{\"lat\":%s,\"lon\":%s}],\"costing\":\"auto\",\"units\":\"kilometers\",\"shape_format\":\"polyline5\"}",
                    fromLat, fromLon, toLat, toLon
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(dataRoute, headers);

            String routeResponse = restTemplate.postForObject(
                    valhallaServerUrl + "/optimized_route",
                    entity,
                    String.class
            );

            ObjectMapper routeMapper = new ObjectMapper();
            JsonNode root = routeMapper.readTree(routeResponse);

            // Проверяем статус
            int status = root.path("trip").path("status").asInt();
            if (status != 0) {
                String message = root.path("trip").path("status_message").asText();
                return ResponseEntity.badRequest().body("Ошибка маршрута: " + message);
            }

            // Достаем нужные данные
            String shape = root.path("trip").path("legs").get(0).path("shape").asText();
            double timeSec = root.path("trip").path("summary").path("time").asDouble();
            double lengthKm = root.path("trip").path("summary").path("length").asDouble();

            double pricePerKm = 120.48;
            double pickupPrice = 50;
            double fixPrice = 79;
            // Формируем ответ
            Map<String, Object> result = new HashMap<>();
            result.put("shape", shape);
            result.put("time_seconds", timeSec);
            result.put("time_minutes", Math.round(timeSec / 60 * 10) / 10.0);
            result.put("length_km", lengthKm);
            result.put("length_meters", Math.round(lengthKm * 1000));
            result.put("price", lengthKm <= 1.3 ? fixPrice : (Math.round(((lengthKm * 13 + fixPrice) - 13)  / 10) * 10));

            return ResponseEntity.ok(result);

            //return ResponseEntity.ok(routeResponse);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(("Ошибка определения маршрута. Проверьте адрес"));
        }
    }

}
