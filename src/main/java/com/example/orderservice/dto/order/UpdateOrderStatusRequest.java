package com.example.orderservice.dto.order;

import com.example.orderservice.entity.OrderStatus;

import jakarta.validation.constraints.NotNull;

public class UpdateOrderStatusRequest {

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
