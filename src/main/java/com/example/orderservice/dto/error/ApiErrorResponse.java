package com.example.orderservice.dto.error;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

public class ApiErrorResponse {

	@JsonFormat(shape = JsonFormat.Shape.STRING)
	private final Instant timestamp;
	private final int status;
	private final String error;
	private final String message;
	private final String path;

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
