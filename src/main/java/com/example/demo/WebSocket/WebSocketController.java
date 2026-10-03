package com.example.demo.WebSocket;

import java.security.Principal;
import java.util.Map;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.example.demo.WebSocket.dto.LocationDto;
import com.example.demo.WebSocket.dto.OrderDto;
import com.example.demo.WebSocket.services.DriverPresenceService;
import com.example.demo.WebSocket.services.OrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final OrderService orderService;
    private final DriverPresenceService driverPresenceService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/driversOnline")
    public void getDriversOnline(Principal principal) {
        if (principal == null) return;
        messagingTemplate.convertAndSendToUser(
                principal.getName(),
                "/queue/driversOnline",
                driverPresenceService.getOnlineDriverCount()
        );
    }

    @MessageMapping("/sendOrder")
    public void sendOrder(@Payload OrderDto dto, Principal principal) {
        log.info("📦 User {} created order", principal.getName());
        orderService.createOrder(dto, principal.getName());
    }

    @MessageMapping("/acceptOrder")
    public void acceptOrder(@Payload Map<String, Integer> payload,
                            Principal principal) {

        orderService.acceptOrder(
                payload.get("orderId"),
                Integer.parseInt(principal.getName())
//                Integer.parseInt(payload.get("orderId")),
//                Integer.parseInt(principal.getName()),
//                Integer.parseInt(payload.get("userId"))
        );
    }

    @MessageMapping("/inTime")
    public void workOrder(@Payload Map<String, String> payload) {
        Integer orderId = Integer.parseInt(payload.get("orderId"));
        log.info("🚗 Driver started ride for order: {}", orderId);
        orderService.workOrder(orderId);
    }

    @MessageMapping("/cancelOrder")
    public void cancelOrder(@Payload Integer orderId, Principal principal) {
        Integer userId = Integer.parseInt(principal.getName());
        log.info("❌ User {} requested to cancel order {}", userId, orderId);
        orderService.cancelOrder(orderId, userId);
    }

    @MessageMapping("/cancelOrderByDriver")
    public void cancelOrderByDriver(@Payload Integer orderId, Principal principal) {
        Integer driverId = Integer.parseInt(principal.getName());
        log.info("❌ Driver {} requested to cancel order {}", driverId, orderId);
        orderService.cancelOrderByDriver(orderId, driverId);
    }

    @MessageMapping("/completeOrder")
    public void completeOrder(@Payload Integer orderId, Principal principal) {
        Integer userId = Integer.parseInt(principal.getName());
        log.info("✅ User {} completed order {}", userId, orderId);
        orderService.completeOrder(orderId);
    }

    @MessageMapping("/sendDriverLocation")
    public void sendDriverLocation(@Payload LocationDto location,
                                   Principal principal) {
        System.out.println("📍 LOCATION RECEIVED: " + location);
        messagingTemplate.convertAndSend(
                "/topic/order/" + location.getOrderId() + "/location",
                location
        );

        orderService.updateDriverLocation(
                location,
                Integer.parseInt(principal.getName())
        );
    }

    @MessageMapping("/joinOrderRoom")
    public void joinOrderRoom(@Payload Integer orderId,
                              Principal principal) {

        log.info("🟢 User {} joined room {}", principal.getName(), orderId);
    }
}