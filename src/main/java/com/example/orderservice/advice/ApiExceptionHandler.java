package com.example.orderservice.advice;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.orderservice.dto.error.ApiErrorResponse;
import com.example.orderservice.dto.error.FieldValidationError;
import com.example.orderservice.exception.ResourceNotFoundException;
import com.example.orderservice.exception.UsernameAlreadyExistsException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);
	private static final String INVALID_VALUE_MESSAGE = "Некорректное значение";

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException exception,
			HttpServletRequest request) {
		List<FieldValidationError> fieldErrors = exception.getBindingResult()
				.getFieldErrors()
				.stream()
				.map(fieldError -> new FieldValidationError(
						fieldError.getField(),
						fieldError.getDefaultMessage() == null ? INVALID_VALUE_MESSAGE : fieldError.getDefaultMessage()))
				.sorted(Comparator.comparing(FieldValidationError::getField)
						.thenComparing(FieldValidationError::getMessage))
				.toList();

		return buildResponse(HttpStatus.BAD_REQUEST, "Ошибка валидации", request, fieldErrors);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException exception,
			HttpServletRequest request) {
		return buildResponse(HttpStatus.BAD_REQUEST, "Некорректный JSON", request, List.of());
	}

	@ExceptionHandler(UsernameAlreadyExistsException.class)
	public ResponseEntity<ApiErrorResponse> handleUsernameAlreadyExists(UsernameAlreadyExistsException exception,
			HttpServletRequest request) {
		return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), request, List.of());
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleResourceNotFound(ResourceNotFoundException exception,
			HttpServletRequest request) {
		return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request, List.of());
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiErrorResponse> handleAuthenticationException(AuthenticationException exception,
			HttpServletRequest request) {
		return buildResponse(HttpStatus.UNAUTHORIZED, "Неверные учётные данные", request, List.of());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception,
			HttpServletRequest request) {
		if (exception instanceof ErrorResponse errorResponse) {
			HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
			if (status != null) {
				return buildResponse(status, status.getReasonPhrase(), request, List.of());
			}
		}

		LOGGER.error("Непредвиденная ошибка при обработке {} {}", request.getMethod(), request.getRequestURI(), exception);
		return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера", request, List.of());
	}

	private ResponseEntity<ApiErrorResponse> buildResponse(HttpStatus status, String message,
			HttpServletRequest request, List<FieldValidationError> fieldErrors) {
		ApiErrorResponse response = new ApiErrorResponse(
				Instant.now(),
				status.value(),
				status.getReasonPhrase(),
				message,
				request.getRequestURI(),
				fieldErrors);

		return ResponseEntity.status(status).body(response);
	}
}
