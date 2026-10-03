package com.example.demo.WebSocket.services;

import com.example.demo.WebSocket.entity.Order;
import com.example.demo.WebSocket.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class OrderTimeoutService {

    private final OrderRepository orderRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);

    private final Map<Integer, ScheduledFuture<?>> timers =
            new ConcurrentHashMap<>();

    /**
     * Запуск таймера на 5 минут
     */
    public void startTimer(Integer orderId) {
        System.out.println("⏳ Timer started for order " + orderId);

        ScheduledFuture<?> future = scheduler.schedule(() -> {

            Order order = orderRepository.findById(orderId)
                    .orElse(null);

            if (order == null) return;

            // если заказ уже не в статусе CREATED — ничего не делаем
            if (!"created".equals(order.getOrderStatus())) {
                return;
            }

            // отменяем заказ
            order.setOrderStatus("canceled");
            orderRepository.save(order);

            // уведомляем всех водителей
            messagingTemplate.convertAndSend(
                    "/topic/orderCanceled",
                    orderId
            );

            // уведомляем пассажира
            messagingTemplate.convertAndSendToUser(
                    order.getUserId().toString(),
                    "/queue/orderCanceled",
                    orderId
            );

            timers.remove(orderId);

        }, 5, TimeUnit.MINUTES);

        timers.put(orderId, future);
    }

    /**
     * Остановка таймера (если заказ приняли)
     */
    public void stopTimer(Integer orderId) {

        ScheduledFuture<?> future = timers.remove(orderId);

        if (future != null) {
            future.cancel(false);
        }
    }
}