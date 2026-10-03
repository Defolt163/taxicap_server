package com.example.demo.WebSocket.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.example.demo.WebSocket.dto.LocationDto;
import com.example.demo.WebSocket.dto.OrderDto;
import com.example.demo.WebSocket.entity.Account;
import com.example.demo.WebSocket.entity.Order;
import com.example.demo.WebSocket.repository.AccountRepository;
import com.example.demo.WebSocket.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {

    @Autowired
    private AccountRepository accountRepository;
    private final OrderRepository orderRepository;
    private final OrderTimeoutService orderTimeoutService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${push.notify-url:http://localhost:3000/api/push/notify}")
    private String pushNotifyUrl;

    @Value("${push.internal-secret:}")
    private String pushInternalSecret;

    public synchronized Order createOrder(OrderDto dto, String userId) {
        try {
            Integer passengerId = Integer.parseInt(userId);
            if (!orderRepository.findByUserIdAndOrderStatusIn(
                    passengerId, List.of("created", "active", "processed"))
                    .isEmpty()) {
                throw new IllegalStateException("У пассажира уже есть незавершённый заказ");
            }

            Order order = new Order();
            order.setCustomerPhone(Long.parseLong(dto.getCustomerPhone()));
            order.setUserId(passengerId);
            order.setOrderStatus("created");
            order.setCustomerName(dto.getCustomerName());
            order.setLatFrom(String.valueOf(dto.getLatFrom()));
            order.setLonFrom(String.valueOf(dto.getLonFrom()));
            order.setLatTo(String.valueOf(dto.getLatTo()));
            order.setLonTo(String.valueOf(dto.getLonTo()));
            order.setAddressFrom(dto.getAddressFrom());
            order.setAddressTo(dto.getAddressTo());
            order.setPrice(dto.getPrice().intValue());
            order.setPaymentMethod(dto.getPaymentMethod() != null ? dto.getPaymentMethod() : "Наличные");
            order.setCustomerImage(dto.getCustomerImage());
            order.setEncodedWay(dto.getEncodedWay());
            order.setRouteDistanceMeters(dto.getRouteDistanceMeters());
            order.setRouteDistanceKm(dto.getRouteDistanceKm());

            Order saved = orderRepository.save(order);
            orderTimeoutService.startTimer(saved.getId());
            messagingTemplate.convertAndSendToUser(userId, "/queue/orderCreated", saved.getId());
            messagingTemplate.convertAndSend("/topic/orders", saved.getId());
            sendPush(List.of(), List.of(1), "Новый заказ", "Появился новый заказ пассажира", saved.getId());
            return saved;
        } catch (Exception e) {
            System.out.print("❌ Order creation failed");
            return null;
        }
    }

    @Transactional
    public void acceptOrder(Integer orderId, Integer driverId) {
        Account driver = accountRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Водитель не найден"));

        if (orderRepository.claimCreatedOrder(orderId) != 1) {
            throw new IllegalStateException("Заказ уже принят или недоступен");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ не найден"));
        order.setDriverId(driver.getUserId());
        order.setDriverName(driver.getUserName());
        order.setDriverPhone(driver.getUserPhone());
        order.setVehicleBrand(driver.getVehicleBrand());
        order.setVehicleModel(driver.getVehicleModel());
        order.setVehicleColor(driver.getVehicleColor());
        order.setVehicleNumber(driver.getVehicleNumber());
        order.setDriverImage(driver.getUserImage());
        orderRepository.save(order);
        accountRepository.save(driver);
        orderTimeoutService.stopTimer(orderId);

        messagingTemplate.convertAndSend("/topic/orderAccepted", orderId);
        messagingTemplate.convertAndSendToUser(order.getUserId().toString(), "/queue/orderAccepted", order);
        messagingTemplate.convertAndSendToUser(driverId.toString(), "/queue/orderAccepted", order);
        sendPush(List.of(order.getUserId()), List.of(), "Водитель найден", "Водитель принял ваш заказ", orderId);
    }

    public void workOrder(Integer orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ не найден"));
        order.setOrderStatus("processed");
        orderRepository.save(order);
        messagingTemplate.convertAndSend("/topic/order/" + orderId + "/status", "processed");
        sendPush(List.of(order.getUserId()), List.of(), "Поездка началась", "Не забудьте пристегнуться", orderId);
    }

    public void cancelOrder(Integer orderId, Integer userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ не найден"));
        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("Вы не можете отменить этот заказ");
        }

        String currentStatus = order.getOrderStatus();
        order.setOrderStatus("canceled");
        orderRepository.save(order);
        orderTimeoutService.stopTimer(orderId);
        messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/orderCanceled", orderId);

        if ("active".equals(currentStatus) || "processed".equals(currentStatus)) {
            if (order.getDriverId() != null) {
                messagingTemplate.convertAndSendToUser(
                        order.getDriverId().toString(), "/queue/orderCanceledByPassenger", orderId);
                sendPush(List.of(order.getDriverId()), List.of(), "Заказ отменён", "Пассажир отменил поездку", orderId);
            }
        }

        messagingTemplate.convertAndSend("/topic/orderCanceled", orderId);
    }

    public void cancelOrderByDriver(Integer orderId, Integer driverId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ не найден"));
        if (order.getDriverId() == null || !order.getDriverId().equals(driverId)) {
            throw new RuntimeException("Вы не можете отменить этот заказ");
        }

        String currentStatus = order.getOrderStatus();
        if (!"active".equals(currentStatus) && !"processed".equals(currentStatus)) {
            throw new RuntimeException("Заказ нельзя отменить в текущем статусе: " + currentStatus);
        }

        order.setOrderStatus("canceled");
        orderRepository.save(order);
        orderTimeoutService.stopTimer(orderId);
        messagingTemplate.convertAndSendToUser(order.getUserId().toString(), "/queue/orderCanceledByDriver", orderId);
        messagingTemplate.convertAndSendToUser(driverId.toString(), "/queue/orderCanceled", orderId);
        messagingTemplate.convertAndSend("/topic/orderCanceled", orderId);
        sendPush(List.of(order.getUserId()), List.of(), "Заказ отменён", "Водитель отменил поездку", orderId);
    }

    public void completeOrder(Integer orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ не найден"));
        if (!"processed".equals(order.getOrderStatus())) {
            throw new RuntimeException("Заказ нельзя завершить в статусе: " + order.getOrderStatus());
        }

        order.setOrderStatus("complete");
        orderRepository.save(order);
        messagingTemplate.convertAndSendToUser(order.getUserId().toString(), "/queue/orderCompleted", orderId);
        if (order.getDriverId() != null) {
            messagingTemplate.convertAndSendToUser(order.getDriverId().toString(), "/queue/orderCompleted", orderId);
        }
        sendPush(List.of(order.getUserId(), order.getDriverId()), List.of(), "Поездка завершена", "Заказ завершён", orderId);
    }

    public void updateDriverLocation(LocationDto location, Integer driverId) {
        messagingTemplate.convertAndSend("/topic/order/" + location.getOrderId() + "/location", location);
    }

    private void sendPush(List<Integer> userIds, List<Integer> roles, String title, String body, Integer orderId) {
        if (pushInternalSecret == null || pushInternalSecret.isBlank()) return;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-push-secret", pushInternalSecret);

        Map<String, Object> payload = new HashMap<>();
        payload.put("userIds", userIds.stream().filter(java.util.Objects::nonNull).toList());
        payload.put("roles", roles);
        payload.put("title", title);
        payload.put("body", body);
        payload.put("data", Map.of("orderId", orderId));

        try {
            var response = restTemplate.postForEntity(
                    pushNotifyUrl,
                    new HttpEntity<>(payload, headers),
                    Map.class
            );
            System.out.println("Push dispatch result: " + response.getBody());
        } catch (Exception error) {
            System.err.println("Push notification failed: " + error.getMessage());
        }
    }
}
