package com.example.orderservice.dto.auth;

import com.example.orderservice.validation.MaxUtf8Bytes;
import com.example.orderservice.validation.MinCodePoints;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Учётные данные для входа")
public class LoginRequest {

	@Schema(description = "Зарегистрированный username", example = "user_1",
			minLength = 3, maxLength = 50, pattern = "[a-zA-Z0-9_]+")
	@NotBlank(message = "Username обязателен")
	@Size(min = 3, max = 50, message = "Username должен содержать от 3 до 50 символов")
	@Pattern(regexp = "[a-zA-Z0-9_]+", message = "Username может содержать только латинские буквы, цифры и знак подчёркивания")
	private String username;

	@Schema(description = "Пароль пользователя", example = "strong-password",
			format = "password", minLength = 8)
	@NotBlank(message = "Пароль обязателен")
	@MinCodePoints(value = 8, message = "Пароль должен содержать не менее 8 Unicode-символов")
	@MaxUtf8Bytes(value = 72, message = "Пароль должен занимать не более 72 байт в UTF-8")
	private String password;

	public LoginRequest() {
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
}
