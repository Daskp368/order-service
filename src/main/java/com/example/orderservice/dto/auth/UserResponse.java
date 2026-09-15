package com.example.orderservice.dto.auth;

import java.util.UUID;

import com.example.orderservice.entity.Role;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Безопасное представление пользователя без пароля")
public class UserResponse {

	@Schema(description = "UUID пользователя", example = "0dbdfd3c-439c-46ab-ab78-a7cbe86d540f",
			format = "uuid")
	private final UUID id;
	@Schema(description = "Нормализованный username", example = "user_1")
	private final String username;
	@Schema(description = "Роль пользователя", example = "USER")
	private final Role role;

	public UserResponse(UUID id, String username, Role role) {
		this.id = id;
		this.username = username;
		this.role = role;
	}

	public UUID getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public Role getRole() {
		return role;
	}
}
