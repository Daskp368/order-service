package com.example.orderservice.dto.error;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ошибка валидации конкретного поля")
public class FieldValidationError {

	@Schema(description = "Имя поля", example = "description")
	private final String field;
	@Schema(description = "Сообщение правила валидации", example = "Описание заказа обязательно")
	private final String message;

	public FieldValidationError(String field, String message) {
		this.field = field;
		this.message = message;
	}

	public String getField() {
		return field;
	}

	public String getMessage() {
		return message;
	}
}
