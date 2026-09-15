package com.example.orderservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.orderservice.entity.Order;

public interface OrderRepository extends JpaRepository<Order, UUID> {

	Page<Order> findAllByUser_Id(UUID userId, Pageable pageable);

	Optional<Order> findByIdAndUser_Id(UUID orderId, UUID userId);
}
