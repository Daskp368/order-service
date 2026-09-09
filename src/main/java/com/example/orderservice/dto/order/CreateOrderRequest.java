package com.example.orderservice.dto.order;

import com.example.orderservice.validation.TrimmedSize;

import jakarta.validation.constraints.NotBlank;

public class CreateOrderRequest {

	@NotBlank(message = "Описание заказа обязательно")
	@TrimmedSize(min = 1, max = 1000,
			message = "Описание заказа после удаления пробелов по краям должно содержать от 1 до 1000 символов")
	private String description;

	public CreateOrderRequest() {
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}
