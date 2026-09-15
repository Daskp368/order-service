package com.example.orderservice.dto.error;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Единый формат ошибки API")
public class ApiErrorResponse {

	@Schema(description = "Момент формирования ошибки", example = "2026-09-16T10:30:00Z",
			format = "date-time")
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	private final Instant timestamp;
	@Schema(description = "Числовой HTTP-статус", example = "400")
	private final int status;
	@Schema(description = "Стандартное название HTTP-статуса", example = "Bad Request")
	private final String error;
	@Schema(description = "Безопасное описание ошибки", example = "Ошибка валидации")
	private final String message;
	@Schema(description = "Путь запроса, на котором возникла ошибка", example = "/api/orders")
	private final String path;

	@Schema(description = "Ошибки отдельных полей; отсутствуют в JSON, если список пуст")
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	private final List<FieldValidationError> fieldErrors;

	public ApiErrorResponse(Instant timestamp, int status, String error, String message, String path,
			List<FieldValidationError> fieldErrors) {
		this.timestamp = timestamp;
		this.status = status;
		this.error = error;
		this.message = message;
		this.path = path;
		this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public int getStatus() {
		return status;
	}

	public String getError() {
		return error;
	}

	public String getMessage() {
		return message;
	}

	public String getPath() {
		return path;
	}

	public List<FieldValidationError> getFieldErrors() {
		return fieldErrors;
	}
}
