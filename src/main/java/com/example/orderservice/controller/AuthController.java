package com.example.orderservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.orderservice.config.OpenApiConfig;
import com.example.orderservice.dto.auth.LoginRequest;
import com.example.orderservice.dto.auth.LoginResponse;
import com.example.orderservice.dto.auth.RegisterRequest;
import com.example.orderservice.dto.auth.UserResponse;
import com.example.orderservice.dto.error.ApiErrorResponse;
import com.example.orderservice.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping(value = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Аутентификация", description = "Регистрация, вход и информация о текущем пользователе")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	@Operation(summary = "Зарегистрировать пользователя",
			description = "Создаёт пользователя с ролью USER и сохраняет BCrypt-хеш пароля")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Пользователь создан", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", description = "Ошибка валидации или некорректный JSON",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "Username уже занят",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
		UserResponse response = authService.register(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PostMapping("/login")
	@Operation(summary = "Войти в систему",
			description = "Проверяет учётные данные и возвращает Bearer JWT")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Учётные данные верны", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", description = "Ошибка валидации или некорректный JSON",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "Неверные учётные данные",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok(authService.login(request));
	}

	@GetMapping("/me")
	@Operation(summary = "Получить текущего пользователя",
			description = "Возвращает безопасный профиль владельца предъявленного JWT")
	@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Текущий пользователь найден", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<UserResponse> currentUser(@Parameter(hidden = true) Authentication authentication) {
		return ResponseEntity.ok(authService.getCurrentUser(authentication.getName()));
	}
}
