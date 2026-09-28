package com.example.orderservice.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Результат успешной аутентификации")
public class LoginResponse {

	@Schema(description = "Подписанный JWT", example = "eyJhbGciOiJIUzI1NiJ9...")
	private final String token;
	@Schema(description = "Схема заголовка Authorization", example = "Bearer")
	private final String tokenType;
	@Schema(description = "Срок действия токена в секундах", example = "1800", minimum = "1")
	private final long expiresIn;

	public LoginResponse(String token, String tokenType, long expiresIn) {
		this.token = token;
		this.tokenType = tokenType;
		this.expiresIn = expiresIn;
	}

	public String getToken() {
		return token;
	}

	public String getTokenType() {
		return tokenType;
	}

	public long getExpiresIn() {
		return expiresIn;
	}
}
