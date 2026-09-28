package com.example.orderservice.dto.order;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.orderservice.entity.OrderStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Представление заказа")
public class OrderResponse {

	@Schema(description = "UUID заказа", example = "2715fc32-0bc4-435d-ad2d-d09a7f19c607",
			format = "uuid")
	private final UUID id;
	@Schema(description = "UUID владельца заказа", example = "0dbdfd3c-439c-46ab-ab78-a7cbe86d540f",
			format = "uuid")
	private final UUID userId;
	@Schema(description = "Описание заказа", example = "Новый заказ")
	private final String description;
	@Schema(description = "Текущий статус заказа", example = "CREATED")
	private final OrderStatus status;
	@Schema(description = "Время создания заказа на сервере", example = "2026-09-16T10:30:00",
			format = "date-time")
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
