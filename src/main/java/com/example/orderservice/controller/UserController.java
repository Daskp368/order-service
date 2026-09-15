package com.example.orderservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.orderservice.config.OpenApiConfig;
import com.example.orderservice.dto.auth.UserResponse;
import com.example.orderservice.dto.error.ApiErrorResponse;
import com.example.orderservice.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(value = "/api/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Пользователи", description = "Административные операции с пользователями")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	@Operation(summary = "Получить всех пользователей",
			description = "Возвращает ADMIN список пользователей, отсортированный по username")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Список пользователей получен", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "Требуется роль ADMIN",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<List<UserResponse>> getAllUsers() {
		return ResponseEntity.ok(userService.getAllUsers());
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Удалить пользователя",
			description = "Удаляет пользователя и его заказы. ADMIN не может удалить собственную учётную запись")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "Пользователь и его заказы удалены"),
			@ApiResponse(responseCode = "400", description = "Некорректный UUID",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "Требуется роль ADMIN",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Пользователь не найден",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "ADMIN пытается удалить себя",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<Void> deleteUser(
			@Parameter(description = "UUID удаляемого пользователя",
					example = "0dbdfd3c-439c-46ab-ab78-a7cbe86d540f")
			@PathVariable UUID id,
			@Parameter(hidden = true) Authentication authentication) {
		userService.deleteUser(id, authentication.getName());
		return ResponseEntity.noContent().build();
	}
}
