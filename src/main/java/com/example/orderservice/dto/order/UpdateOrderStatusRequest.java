package com.example.orderservice.dto.order;

import com.example.orderservice.entity.OrderStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Данные для изменения статуса заказа")
public class UpdateOrderStatusRequest {

	@Schema(description = "Новый статус заказа", example = "IN_PROGRESS")
	@NotNull(message = "Статус заказа обязателен")
	private OrderStatus status;

	public UpdateOrderStatusRequest() {
	}

	public OrderStatus getStatus() {
		return status;
	}

	public void setStatus(OrderStatus status) {
		this.status = status;
	}
}
