package com.example.demo.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class TileProxyController {

    @Autowired
    private JwtValidator jwtValidator;

    @Value("${services.tiles.url:http://localhost:8001}")
    private String tileServerUrl;
    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/tile/**")
    public ResponseEntity<byte[]> getTile(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {

        // 1. Проверяем наличие заголовка
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Missing or invalid Authorization header".getBytes());
        }

        String token = authHeader.substring(7);

        // 2. Проверяем JWT
        try {
            jwtValidator.validateToken(token);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(("Invalid JWT: " + e.getMessage()).getBytes());
        }

        // 3. Формируем запрос к тайловому серверу
        String requestPath = request.getRequestURI();
        String tilePath = requestPath.substring("/tile".length());
        String tileUrl = tileServerUrl + "/tile" + tilePath;
        //System.out.println("🔍 Tile path: " + tilePath);

        try {
                ResponseEntity<byte[]> response = restTemplate.getForEntity(tileUrl, byte[].class);

            HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.IMAGE_PNG);

                return ResponseEntity.ok()
                    .headers(headers)
                    .body(response.getBody());

        } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(("Tile not found: " + tilePath).getBytes());
        }
    }
}