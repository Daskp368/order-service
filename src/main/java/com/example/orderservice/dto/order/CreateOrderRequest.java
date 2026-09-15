package com.example.orderservice.dto.order;

import com.example.orderservice.validation.TrimmedSize;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Данные для создания заказа")
public class CreateOrderRequest {

	@Schema(description = "Описание заказа после удаления пробелов по краям",
			example = "Новый заказ", minLength = 1, maxLength = 1000)
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
