package com.example.demo.WebSocket.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.WebSocket.entity.Order;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {

	List<Order> findByUserIdAndOrderStatusIn(Integer userId, List<String> statuses);

	@Modifying
	@Query("update Order o set o.orderStatus = 'active' where o.id = :orderId and o.orderStatus = 'created'")
	int claimCreatedOrder(@Param("orderId") Integer orderId);
}