package com.example.orderservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.orderservice.entity.Order;

public interface OrderRepository extends JpaRepository<Order, UUID> {

	List<Order> findAllByUser_IdOrderByCreatedAtDesc(UUID userId);

	Optional<Order> findByIdAndUser_Id(UUID orderId, UUID userId);

	List<Order> findAllByOrderByCreatedAtDesc();
}
