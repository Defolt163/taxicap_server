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

    private final String NOMINATION_SERVER_URL = "http://localhost:8003";
    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/api/geo/**")
    public ResponseEntity<?> processRequestBody(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> requestBody) {

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

        String fromAddress = requestBody.get("from");
        String toAddress = requestBody.get("to");

//        System.out.println("From: " + fromAddress);
//        System.out.println("To: " + toAddress);

        if (fromAddress == null || toAddress == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Ошибка ввода");
        }

        String nominationUrlFrom = NOMINATION_SERVER_URL + "/search?viewbox=" + geoborder + "&format=jsonv2&q="
                + fromAddress + "&bounded=1";
        String nominationUrlTo = NOMINATION_SERVER_URL + "/search?viewbox=" + geoborder + "&format=jsonv2&q="
                + toAddress + "&bounded=1";
        //System.out.print(nominationUrl);
        try {
            ObjectMapper mapper = new ObjectMapper();

            String responseFrom = restTemplate.getForObject(nominationUrlFrom, String.class);
            JsonNode[] resultsFrom = mapper.readValue(responseFrom, JsonNode[].class);

            if (resultsFrom.length == 0) {
                return ResponseEntity.badRequest().body("Адрес отправления не найден");
            }

            String fromLat = resultsFrom[0].get("lat").asText();
            String fromLon = resultsFrom[0].get("lon").asText();

            // Задержка в 1 секунду (согласно политике Nominatim)
            Thread.sleep(1000);

            // Получаем координаты TO
            String responseTo = restTemplate.getForObject(nominationUrlTo, String.class);
            JsonNode[] resultsTo = mapper.readValue(responseTo, JsonNode[].class);

            if (resultsTo.length == 0) {
                return ResponseEntity.badRequest().body("Адрес назначения не найден, или находится вне зоны действия приложения");
            }

            String toLat = resultsTo[0].get("lat").asText();
            String toLon = resultsTo[0].get("lon").asText();

            //
            // System.out.print(toLat);

            //String routeGetPolylines = "http://localhost:8002/optimized_route";

            String dataRoute = String.format(
                    "{\"locations\":[{\"lat\":%s,\"lon\":%s},{\"lat\":%s,\"lon\":%s}],\"costing\":\"auto\",\"units\":\"kilometers\",\"shape_format\":\"polyline5\"}",
                    fromLat, fromLon, toLat, toLon
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(dataRoute, headers);

            String routeResponse = restTemplate.postForObject(
                    "http://localhost:8002/optimized_route",
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

            // Формируем ответ
            Map<String, Object> result = new HashMap<>();
            result.put("shape", shape);
            result.put("time_seconds", timeSec);
            result.put("time_minutes", Math.round(timeSec / 60 * 10) / 10.0);
            result.put("length_km", lengthKm);
            result.put("length_meters", Math.round(lengthKm * 1000));
            result.put("price", Math.round((lengthKm * 1000) * 0.045 + 45));

            return ResponseEntity.ok(result);

            //return ResponseEntity.ok(routeResponse);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(("Ошибка определения маршрута. Проверьте адрес"));
        }
    }

}
