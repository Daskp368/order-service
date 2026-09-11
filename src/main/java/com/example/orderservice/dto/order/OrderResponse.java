package com.example.orderservice.dto.order;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.orderservice.entity.OrderStatus;

public class OrderResponse {

	private final UUID id;
	private final UUID userId;
	private final String description;
	private final OrderStatus status;
	private final LocalDateTime createdAt;

	public OrderResponse(UUID id, UUID userId, String description, OrderStatus status, LocalDateTime createdAt) {
		this.id = id;
		this.userId = userId;
		this.description = description;
		this.status = status;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getDescription() {
		return description;
	}

	public OrderStatus getStatus() {
		return status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
